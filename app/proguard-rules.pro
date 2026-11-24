# R8 / ProGuard rules for Running.
#
# MapLibre, Play services location, Room, WorkManager, DataStore, Navigation and
# kotlinx.serialization ship their own consumer rules inside their artifacts, so
# they are not repeated here. Add a rule only for a proven release crash, with a
# comment saying which one.

# Keep file/line info in crash reports — symbolicate via mapping.txt produced
# at build/outputs/mapping/release/.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
