/**
 * kalman_filter.c — 1D Kalman Filter implementation.
 *
 * Direct port of KalmanFilter1D.kt to C for Android NDK integration.
 * Algorithm is mathematically identical to the Kotlin version; this
 * file exists solely for the JNI performance benchmark (Phase G.4).
 */

#include "kalman_filter.h"
#include <stdlib.h>
#include <math.h>

/* Internal struct definition (hidden from callers via opaque pointer) */
struct KalmanFilter1D {
    float q;  /* Process noise variance */
    float r;  /* Measurement noise variance */
    float x;  /* State estimate */
    float p;  /* Estimation error covariance */
};

/* ── Lifecycle ─────────────────────────────────────────────────────────── */

KalmanFilter1D* kalman_create(float q, float r) {
    KalmanFilter1D* filter = (KalmanFilter1D*)malloc(sizeof(KalmanFilter1D));
    if (filter == NULL) return NULL;

    filter->q = q;
    filter->r = r;
    filter->x = 0.0f;  /* Initial state estimate */
    filter->p = 1.0f;  /* Initial error covariance */
    return filter;
}

void kalman_destroy(KalmanFilter1D* filter) {
    if (filter != NULL) {
        free(filter);
    }
}

void kalman_reset(KalmanFilter1D* filter) {
    if (filter == NULL) return;
    filter->x = 0.0f;
    filter->p = 1.0f;
}

/* ── Core algorithm ────────────────────────────────────────────────────── */

float kalman_update(KalmanFilter1D* filter, float z) {
    if (filter == NULL) return z;  /* Passthrough on null — safe fallback */

    /* Prediction step: grow uncertainty */
    filter->p += filter->q;

    /* Update step: incorporate measurement */
    float k = filter->p / (filter->p + filter->r);  /* Kalman gain */
    filter->x += k * (z - filter->x);               /* State correction */
    filter->p *= (1.0f - k);                         /* Covariance update */

    return filter->x;
}

/* ── 3D convenience wrapper ────────────────────────────────────────────── */

void kalman_update_3d(
    KalmanFilter1D* fx, KalmanFilter1D* fy, KalmanFilter1D* fz,
    float in_x,  float in_y,  float in_z,
    float* out_x, float* out_y, float* out_z
) {
    *out_x = kalman_update(fx, in_x);
    *out_y = kalman_update(fy, in_y);
    *out_z = kalman_update(fz, in_z);
}
