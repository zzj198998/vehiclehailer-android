# ProGuard rules for VehicleHailer
# 保留所有模型类（反射访问）
-keep class com.egogame.vehiclehailer.model.** { *; }
-keep class com.egogame.vehiclehailer.config.** { *; }

# 保留 Gson 序列化/反序列化
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }

# 保留 VehicleHailerApp
-keep class com.egogame.vehiclehailer.VehicleHailerApp { *; }
