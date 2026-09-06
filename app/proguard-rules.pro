# Reglas ProGuard/R8 para el build release.
# Compose y AndroidX ya traen sus reglas por defecto; se añadirán aquí solo las
# específicas de MASTER cuando sea necesario.

# WorkManager (TD-068). Dos cosas se buscan POR NOMBRE en tiempo de ejecucion, y R8 las
# renombraba: la clase Room generada donde WorkManager guarda su cola —sin ella el app
# moria al arrancar, antes siquiera de llegar a onCreate (v1.0.184)— y el propio Worker,
# que WorkManager instancia con Class.forName a partir del nombre guardado en esa cola.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep class androidx.work.impl.WorkDatabase_Impl { *; }
-keep class * extends androidx.work.ListenableWorker {
    <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class com.maurozegarra.master.notify.AssignmentWorker
# El InputMerger tambien se instancia por reflexion. Las reglas que trae la propia
# libreria mantienen estas clases pero NO sus constructores, y R8 en modo full borra un
# constructor que nadie llama desde el codigo: el resultado era que ningun worker llegaba
# a ejecutarse, con un unico rastro en el log ("Could not create Input Merger"). De ahi que
# aqui todas las reglas nombren el constructor explicitamente.
-keep class * extends androidx.work.InputMerger { <init>(); }
