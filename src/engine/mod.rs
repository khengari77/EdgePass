use image::{DynamicImage, GenericImageView, ImageBuffer, ImageOutputFormat, Rgba, RgbaImage};
use log::info;
use std::io::Cursor;

use crate::standards::PassportStandard;

#[derive(Clone, Copy, Debug)]
pub struct CropConfig {
    pub target_width: u32,
    pub target_height: u32,
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
        _face_center: Option<(f32, f32)>,
        remove_background: bool,
    ) -> Result<Vec<u8>, String> {
        info!("Processing image with standard: {:?}", standard);
        info!("Remove background: {}", remove_background);

        let img = image::load_from_memory(image_bytes)
            .map_err(|e| format!("Failed to decode image: {}", e))?;

        info!("Input image dimensions: {}x{}", img.width(), img.height());

        let config = standard.to_config();
        info!(
            "Target dimensions: {}x{}",
            config.target_width, config.target_height
        );

        let processed = self.apply_white_bg_and_resize(&img, config, remove_background)?;

        info!(
            "Processing complete, output dimensions: {}x{}",
            processed.width(),
            processed.height()
        );

        let mut output_bytes = Vec::new();
        let mut cursor = Cursor::new(&mut output_bytes);
        processed
            .write_to(&mut cursor, ImageOutputFormat::Jpeg(95))
            .map_err(|e| format!("Failed to encode output: {}", e))?;

        Ok(output_bytes)
    }

    fn apply_white_bg_and_resize(
        &self,
        img: &DynamicImage,
        config: CropConfig,
        remove_background: bool,
    ) -> Result<RgbaImage, String> {
        let (input_width, input_height) = (img.width(), img.height());
        let target_width = config.target_width;
        let target_height = config.target_height;

        info!(
            "Resizing from {}x{} to {}x{}",
            input_width, input_height, target_width, target_height
        );

        let resized = img.resize_exact(
            target_width,
            target_height,
            image::imageops::FilterType::Lanczos3,
        );

        let alpha_mask = if remove_background {
            info!("Creating alpha mask for background removal");
            Some(self.create_alpha_mask(&resized))
        } else {
            None
        };

        let mut output: RgbaImage =
            ImageBuffer::from_pixel(target_width, target_height, Rgba([255, 255, 255, 255]));

        info!("Compositing image...");
        for y in 0..target_height {
            for x in 0..target_width {
                let pixel = resized.get_pixel(x, y);

                let is_foreground = match &alpha_mask {
                    Some(mask) => mask.get_pixel(x, y)[0] > 128,
                    None => true,
                };

                if is_foreground {
                    output.put_pixel(x, y, Rgba([pixel[0], pixel[1], pixel[2], 255]));
                }
            }
        }

        Ok(output)
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
