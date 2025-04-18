# Proguard rules



# fix the bug: java.lang.VerifyError: Bad type on operand stack
-dontoptimize

# fix java.lang.UnsatisfiedLinkError: Can't obtain static method fromNative(Class, Object) from class com.sun.jna.Native
-keepclassmembers class com.sun.jna.* { *; }
