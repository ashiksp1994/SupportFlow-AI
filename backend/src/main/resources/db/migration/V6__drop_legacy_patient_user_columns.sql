-- =============================================================================
-- V6__drop_legacy_patient_user_columns.sql
-- Removes unused legacy patient demographic columns (date_of_birth, gender)
-- inherited from the original Physiotherapy Clinic user_details table.
-- =============================================================================

ALTER TABLE user_details DROP COLUMN IF EXISTS date_of_birth;
ALTER TABLE user_details DROP COLUMN IF EXISTS gender;
