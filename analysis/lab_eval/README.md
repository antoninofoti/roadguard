# Lab Evaluation Toolkit

This folder provides a reproducible evaluation flow for thesis validation without mandatory on-road driving.

## Files

- evaluate_metrics.py: computes TP, FP, FN, Precision, Recall, F1.
- templates/ground_truth.csv: sample ground-truth format.
- templates/predictions.csv: sample predictions format.

## Matching Rule

A prediction is a true positive when:
- distance(prediction, ground-truth) <= radius meters,
- damage type matches (unless --ignore-type is used),
- the ground-truth item is not already matched.

Greedy matching is used (highest confidence first).

## Input Format

Ground truth CSV required columns:
- id
- lat
- lng
- damage_type

Predictions CSV required columns:
- id
- mode
- lat
- lng
- damage_type
- confidence

Optional prediction columns:
- timestamp_ms

## Usage

From repository root:

python3 analysis/lab_eval/evaluate_metrics.py \
  --ground-truth analysis/lab_eval/templates/ground_truth.csv \
  --predictions analysis/lab_eval/templates/predictions.csv \
  --radius-m 25 \
  --output analysis/lab_eval/results.csv

## Output

Console table per mode and overall:
- TP
- FP
- FN
- Precision
- Recall
- F1

If --output is provided, a CSV summary is written.

## Recommended Thesis Modes

Use one mode value for each prediction row:
- cv_only
- sensor_only
- fixed_fusion
- adaptive_fusion
