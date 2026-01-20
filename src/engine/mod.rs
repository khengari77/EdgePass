use image::{DynamicImage, ImageBuffer, ImageOutputFormat, Rgb, Rgba, RgbaImage};
use log::info;

#[derive(Clone, Copy, Debug)]
pub struct CropConfig {
    pub target_width: u32,
    pub target_height: u32,
    pub top_margin_ratio: f32,
}

pub struct PassportEngine;

impl PassportEngine {
    pub fn new(_model_path: &str) -> Self {
        info!("Initializing PassportEngine");
        PassportEngine {}
    }

    pub fn process(
        &self,
        image_bytes: &[u8],
        standard: PassportStandard,
        _suit_bytes: Option<&[u8]>,
        face_center: Option<(f32, f32)>,
        remove_background: bool,
    ) -> Result<Vec<u8>, String> {
        info!("Processing image with standard: {:?}", standard);
        info!("Remove background: {}", remove_background);
        info!("Face center: {:?}", face_center);

        let img = image::load_from_memory(image_bytes)
            .map_err(|e| format!("Failed to decode image: {}", e))?;

        info!("Input image dimensions: {}x{}", img.width(), img.height());

        let config = standard.to_config();
        info!(
            "Crop config: {}x{}, top_margin: {}",
            config.target_width, config.target_height, config.top_margin_ratio
        );

        let processed =
            self.apply_crop_and_white_bg(&img, config, face_center, remove_background)?;

        info!(
            "Processing complete, output dimensions: {}x{}",
            processed.width(),
            processed.height()
        );

        let mut output_bytes = Vec::new();
        processed
            .write_to(&mut output_bytes, ImageOutputFormat::Jpeg(95))
            .map_err(|e| format!("Failed to encode output: {}", e))?;

        Ok(output_bytes)
    }

    fn apply_crop_and_white_bg(
        &self,
        img: &DynamicImage,
        config: CropConfig,
        face_center: Option<(f32, f32)>,
        remove_background: bool,
    ) -> Result<RgbaImage, String> {
        let (img_width, img_height) = (img.width(), img.height());

        let face_x = face_center
            .map(|(x, _)| x)
            .unwrap_or(img_width as f32 / 2.0);
        let face_y = face_center
            .map(|(_, y)| y)
            .unwrap_or(img_height as f32 / 2.0);

        let standard_height = config.target_height;
        let standard_width = config.target_width;

        let crop_width = standard_width;
        let crop_height = standard_height;

        let face_region_height = crop_height * config.top_margin_ratio;

        let mut x_offset = (face_x - crop_width as f32 / 2.0) as i64;
        let mut y_offset = (face_y - face_region_height / 2.0) as i64;

        x_offset = x_offset.clamp(0, (img_width as i64 - crop_width as i64));
        y_offset = y_offset.clamp(0, (img_height as i64 - crop_height as i64));

        let x_offset = x_offset as u32;
        let y_offset = y_offset as u32;

        info!(
            "Cropping at offset ({}, {}) for {}x{}",
            x_offset, y_offset, crop_width, crop_height
        );

        let cropped = img.crop(x_offset, y_offset, crop_width, crop_height);

        info!("remove_background = {}", remove_background);

        let alpha_mask = if remove_background {
            info!("Creating alpha mask for background removal");
            Some(self.create_alpha_mask(&cropped))
        } else {
            info!("No background removal, using all foreground pixels");
            None
        };

        let mut output: RgbaImage =
            ImageBuffer::from_pixel(crop_width, crop_height, Rgba([255, 255, 255, 255]));

        info!("Compositing image...");
        for y in 0..crop_height {
            for x in 0..crop_width {
                let pixel = cropped.get_pixel(x, y);

                let is_foreground = match &alpha_mask {
                    Some(mask) => mask.get_pixel(x, y)[0] > 128,
                    None => true,
                };

                if is_foreground {
                    output.put_pixel(x, y, Rgba([pixel[0], pixel[1], pixel[2], 255]));
                }
            }
        }

        Ok(DynamicImage::ImageRgba8(output))
    }

    fn create_alpha_mask(
        &self,
        img: &DynamicImage,
    ) -> image::ImageBuffer<image::Luma<u8>, Vec<u8>> {
        let (width, height) = img.dimensions();
        info!("Creating alpha mask for {}x{} image", width, height);

        let center_x = width as f32 / 2.0;
        let center_y = height as f32 / 2.0;

        let ellipse_a = width as f32 * 0.40;
        let ellipse_b = height as f32 * 0.50;

        let mut alpha_mask: image::ImageBuffer<image::Luma<u8>, Vec<u8>> =
            ImageBuffer::new(width, height);

        for y in 0..height {
            for x in 0..width {
                let dx = (x as f32 - center_x) / ellipse_a;
                let dy = (y as f32 - center_y) / ellipse_b;
                let dist = (dx * dx + dy * dy).sqrt();

                let alpha = if dist <= 1.0 { 255u8 } else { 0u8 };
                alpha_mask.put_pixel(x, y, image::Luma([alpha]));
            }
        }

        alpha_mask
    }
}
