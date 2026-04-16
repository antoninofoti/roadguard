/**
 * jni_bridge.cpp — JNI bridge between Android/Kotlin and the C Kalman filter.
 *
 * Exposes the native KalmanFilter1D to Kotlin via JNI.
 * The Kotlin class NativeKalmanFilter.kt wraps these functions.
 *
 * JNI naming convention:
 *   Java_<package_path>_<class>_<method>
 *   Package: com.example.roadguard.sensor
 *   Class:   NativeKalmanFilter
 *
 * Memory model:
 *   The native KalmanFilter1D struct is heap-allocated in nativeCreate()
 *   and its pointer is passed back to Kotlin as a jlong (64-bit integer).
 *   Kotlin holds the pointer as a private Long field and must call
 *   nativeDestroy() to release it.
 */

#include <jni.h>
#include "kalman_filter.h"

extern "C" {

/**
 * Create a new native KalmanFilter1D and return its pointer as jlong.
 * The Kotlin caller stores this handle in a private Long field.
 */
JNIEXPORT jlong JNICALL
Java_com_example_roadguard_sensor_NativeKalmanFilter_nativeCreate(
    JNIEnv* /* env */, jobject /* thiz */,
    jfloat q, jfloat r
) {
    KalmanFilter1D* filter = kalman_create(static_cast<float>(q),
                                           static_cast<float>(r));
    return reinterpret_cast<jlong>(filter);
}

/**
 * Process a measurement and return the filtered value.
 */
JNIEXPORT jfloat JNICALL
Java_com_example_roadguard_sensor_NativeKalmanFilter_nativeUpdate(
    JNIEnv* /* env */, jobject /* thiz */,
    jlong handle, jfloat measurement
) {
    auto* filter = reinterpret_cast<KalmanFilter1D*>(handle);
    return static_cast<jfloat>(kalman_update(filter, static_cast<float>(measurement)));
}

/**
 * Reset the filter state to initial values.
 */
JNIEXPORT void JNICALL
Java_com_example_roadguard_sensor_NativeKalmanFilter_nativeReset(
    JNIEnv* /* env */, jobject /* thiz */,
    jlong handle
) {
    auto* filter = reinterpret_cast<KalmanFilter1D*>(handle);
    kalman_reset(filter);
}

/**
 * Free the native memory. Must be called when the Kotlin object is released.
 */
JNIEXPORT void JNICALL
Java_com_example_roadguard_sensor_NativeKalmanFilter_nativeDestroy(
    JNIEnv* /* env */, jobject /* thiz */,
    jlong handle
) {
    auto* filter = reinterpret_cast<KalmanFilter1D*>(handle);
    kalman_destroy(filter);
}

/**
 * Filter a 3-axis reading in a single JNI call (reduces call overhead).
 * Results are written into a pre-allocated float[3] array.
 *
 * @param handleX  Pointer to X-axis filter
 * @param handleY  Pointer to Y-axis filter
 * @param handleZ  Pointer to Z-axis filter
 * @param x, y, z  Raw IMU readings
 * @param result   float[3] array to receive filtered values
 */
JNIEXPORT void JNICALL
Java_com_example_roadguard_sensor_NativeKalmanFilter_nativeUpdate3D(
    JNIEnv* env, jobject /* thiz */,
    jlong handleX, jlong handleY, jlong handleZ,
    jfloat x, jfloat y, jfloat z,
    jfloatArray result
) {
    auto* fx = reinterpret_cast<KalmanFilter1D*>(handleX);
    auto* fy = reinterpret_cast<KalmanFilter1D*>(handleY);
    auto* fz = reinterpret_cast<KalmanFilter1D*>(handleZ);

    float out_x, out_y, out_z;
    kalman_update_3d(fx, fy, fz, x, y, z, &out_x, &out_y, &out_z);

    jfloat buf[3] = { out_x, out_y, out_z };
    env->SetFloatArrayRegion(result, 0, 3, buf);
}

} /* extern "C" */
