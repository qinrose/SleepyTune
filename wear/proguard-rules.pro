# Wear 模块 ProGuard 规则
-keepattributes SourceFile,LineNumberTable

# Data Layer JSON 载荷（org.json 为平台 API，无需保留）
-dontwarn com.google.android.gms.**
