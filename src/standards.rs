use crate::engine::CropConfig;

#[derive(Debug, Clone, Copy, PartialEq)]
pub enum PassportStandard {
    SaudiEVisa,
    US,
    Schengen,
    GeneralID,
    UK,
    India,
    Custom,
}

impl PassportStandard {
    pub fn to_config(self) -> CropConfig {
        match self {
            PassportStandard::SaudiEVisa => CropConfig {
                target_width: 500,
                target_height: 500,
                top_margin_ratio: 0.45,
            },
            PassportStandard::US => CropConfig {
                target_width: 600,
                target_height: 600,
                top_margin_ratio: 0.45,
            },
            PassportStandard::Schengen => CropConfig {
                target_width: 500,
                target_height: 500,
                top_margin_ratio: 0.45,
            },
            PassportStandard::GeneralID => CropConfig {
                target_width: 450,
                target_height: 550,
                top_margin_ratio: 0.45,
            },
            PassportStandard::UK => CropConfig {
                target_width: 350,
                target_height: 450,
                top_margin_ratio: 0.45,
            },
            PassportStandard::India => CropConfig {
                target_width: 350,
                target_height: 500,
                top_margin_ratio: 0.45,
            },
            PassportStandard::Custom => CropConfig {
                target_width: 500,
                target_height: 500,
                top_margin_ratio: 0.45,
            },
        }
    }
}
