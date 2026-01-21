use crate::engine::PassportEngine;
use crate::standards::PassportStandard;
use jni::objects::{JClass, JString};
use jni::sys::{jbyteArray, jfloat, jint, jstring};
use jni::JNIEnv;
use std::panic;
use std::sync::Arc;

static mut ENGINE: Option<Arc<PassportEngine>> = None;
static LOG_INIT: std::sync::Once = std::sync::Once::new();

#[no_mangle]
pub extern "system" fn Java_com_edgepass_lib_PassportProcessor_nativeInitEngine(
    mut env: JNIEnv,
    _class: JClass,
    model_path: jstring,
) {
    LOG_INIT.call_once(|| {
        android_log::init("EdgePass").ok();
    });

    let model_path_str: String = unsafe {
        let j_string = JString::from_raw(model_path);
        env.get_string(j_string).unwrap().into()
    };

    log::info!(
        "Initializing EdgePass Engine with model path: {}",
        model_path_str
    );

    unsafe {
        ENGINE = Some(Arc::new(PassportEngine::new(&model_path_str)));
    }

    log::info!("EdgePass Engine initialized successfully");
}

#[no_mangle]
pub extern "system" fn Java_com_edgepass_lib_PassportProcessor_nativeGenerate(
    env: JNIEnv,
    _class: JClass,
    image_bytes: jbyteArray,
    standard_id: jint,
    suit_bytes: jbyteArray,
    face_center_x: jfloat,
    face_center_y: jfloat,
    remove_background: jint,
) -> jbyteArray {
    log::info!(
        "nativeGenerate called with standard_id: {}, remove_bg: {}",
        standard_id,
        remove_background
    );

    let image_vec: Vec<u8> = env.convert_byte_array(image_bytes).unwrap();
    log::info!("Received image bytes: {}", image_vec.len());

    let standard: PassportStandard = match standard_id {
        0 => PassportStandard::SaudiEVisa,
        1 => PassportStandard::US,
        2 => PassportStandard::Schengen,
        3 => PassportStandard::GeneralID,
        4 => PassportStandard::UK,
        5 => PassportStandard::India,
        _ => PassportStandard::GeneralID,
    };

    let suit_vec: Option<Vec<u8>> = if suit_bytes.is_null() {
        None
    } else {
        Some(env.convert_byte_array(suit_bytes).unwrap())
    };

    let face_center = if face_center_x == -1.0 && face_center_y == -1.0 {
        None
    } else {
        Some((face_center_x, face_center_y))
    };

    let remove_bg = remove_background != 0;
    log::info!(
        "Face center: {:?}, remove_background: {}",
        face_center,
        remove_bg
    );

    let engine_ref = unsafe {
        match &ENGINE {
            Some(e) => {
                log::info!("Engine found, processing...");
                e.clone()
            }
            None => {
                log::error!("Engine not initialized!");
                return std::ptr::null_mut();
            }
        }
    };

    let result = panic::catch_unwind(|| {
        engine_ref.process(
            &image_vec,
            standard,
            suit_vec.as_deref(),
            face_center,
            remove_bg,
        )
    });

    match result {
        Ok(Ok(output_bytes)) => {
            log::info!(
                "Processing successful, output bytes: {}",
                output_bytes.len()
            );
            let output_array = env.new_byte_array(output_bytes.len() as i32).unwrap();
            let jni_bytes: &[i8] = unsafe {
                std::slice::from_raw_parts(output_bytes.as_ptr() as *const i8, output_bytes.len())
            };
            env.set_byte_array_region(output_array, 0, jni_bytes)
                .unwrap();
            output_array
        }
        Ok(Err(e)) => {
            log::error!("Processing failed: {:?}", e);
            std::ptr::null_mut()
        }
        Err(_) => {
            log::error!("Thread panic during processing");
            std::ptr::null_mut()
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_edgepass_lib_PassportProcessor_nativeCheckInit(
    _env: JNIEnv,
    _class: JClass,
) -> jint {
    let initialized = unsafe { ENGINE.is_some() };
    initialized as jint
}

#[no_mangle]
pub extern "system" fn Java_com_edgepass_lib_PassportProcessor_nativeVersion(
    env: JNIEnv,
    _class: JClass,
) -> jstring {
    let version = "EdgePass Core v0.1.0";
    env.new_string(version).unwrap().into_raw()
}
