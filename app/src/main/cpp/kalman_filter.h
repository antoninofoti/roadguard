/**
 * kalman_filter.h — 1D Kalman Filter for IMU sensor noise reduction.
 *
 * C implementation of the KalmanFilter1D algorithm from:
 *   app/src/main/java/com/example/roadguard/sensor/KalmanFilter3D.kt
 *
 * Ported to C/NDK as Phase G.4 of the RoadGuard thesis to demonstrate
 * performance optimisation via native code on Android (JNI).
 *
 * The algorithm implements a constant-velocity 1D scalar Kalman filter:
 *   Prediction:   p = p + q
 *   Kalman gain:  k = p / (p + r)
 *   Correction:   x = x + k * (z - x)
 *                 p = (1 - k) * p
 *
 * References:
 *   Welch & Bishop (2006) "An Introduction to the Kalman Filter"
 *   Ramezani et al. (2013) "Performance of IMU-Based Pedestrian Navigation"
 */

#ifndef ROADGUARD_KALMAN_FILTER_H
#define ROADGUARD_KALMAN_FILTER_H

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Opaque handle to a KalmanFilter1D instance allocated on the heap.
 * Lifetime is managed by the JNI bridge (create / destroy calls).
 */
typedef struct KalmanFilter1D KalmanFilter1D;

/**
 * Allocate and initialise a new KalmanFilter1D.
 *
 * @param q  Process noise variance (how much we trust the motion model)
 * @param r  Measurement noise variance (how much we trust raw sensors)
 * @return   Heap-allocated filter; caller must call kalman_destroy().
 */
KalmanFilter1D* kalman_create(float q, float r);

/**
 * Process a new sensor measurement and return the filtered value.
 *
 * @param filter  Pointer to the filter instance
 * @param z       Raw sensor reading
 * @return        Filtered (smoothed) value
 */
float kalman_update(KalmanFilter1D* filter, float z);

/**
 * Reset the filter to its initial state (x=0, p=1).
 *
 * @param filter  Pointer to the filter instance
 */
void kalman_reset(KalmanFilter1D* filter);

/**
 * Free all memory associated with the filter.
 *
 * @param filter  Pointer to the filter instance (set to NULL after call)
 */
void kalman_destroy(KalmanFilter1D* filter);

/**
 * Convenience: filter a 3-axis IMU reading in a single call.
 * The caller is responsible for managing three KalmanFilter1D instances,
 * one per axis. Results are written to out_x, out_y, out_z.
 */
void kalman_update_3d(
    KalmanFilter1D* fx, KalmanFilter1D* fy, KalmanFilter1D* fz,
    float in_x, float in_y, float in_z,
    float* out_x, float* out_y, float* out_z
);

#ifdef __cplusplus
}
#endif

#endif /* ROADGUARD_KALMAN_FILTER_H */
