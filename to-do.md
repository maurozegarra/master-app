# To-Do - MASTER

> Generado automaticamente por `forge-status.ps1` desde `docs/forge-todo.json`.
> No editar directamente; actualizar el JSON y regenerar con `.\forge-status.ps1`.
> Convencion de commits: `feat: TD-XXX ...` / `fix: TD-XXX ...`.

Progreso: **98 / 154** hechos, 56 pendientes.

## Pendientes

### Bug

- [ ] **TD-149** Borrar una sesion en el telefono del atleta no la borra del servidor
  - LO QUE PASO, el 21-sep-2026: el coach le hizo a NIKO una demostracion del app en su telefono, con NIKO 1 y NIKO 2, y despues borro esas dos sesiones. Ya habian subido (TD-126) y el servidor no se entero del borrado: el telefono del coach las siguio bajando y el asistente las leyo como entrenamientos de ella -5 y 9 minutos, 'completos'-. Se quitaron a mano con un delete en el SQL Editor. Quedan en el servidor, por lo mismo, tres sesiones a medias del 19-sep (23:45-23:55) que ya no estan en su telefono.

POR QUE: es la regla 1 de TD-126, a proposito. El telefono del atleta solo puede SUBIR, por upload_session, sin leer ni borrar, porque va sin cuenta y con la clave publica. El borrado se quedo sin camino.

CONSECUENCIA: su historial, el del coach y el respaldo que lee el asistente no dicen lo mismo, y el que lee el asistente es justo el que esta mal. Entrenarla con datos falsos es peor que sin datos.

LO QUE HARIA FALTA: una funcion delete_session(p_profile_id, p_session_id) en Supabase, security definer como upload_session, que borre SOLO la sesion de ese perfil con ese id. El app la llama en deleteSession y clearHistory si hay perfil, y quita la marca del ledger de subidas. Sin red, el borrado queda pendiente y se reintenta en la siguiente sincronizacion, igual que la subida. El SQL lo corre el usuario.

A DECIDIR: si borrar todo el historial en el telefono del atleta tambien lo borra en el servidor, o solo el borrado de una sesion.

RIESGO QUE QUEDA: quien conozca el id de perfil y el id de una sesion podria borrarla con la clave publica. Es el mismo nivel que ya tiene upload_session, que puede pisarla.

DESCARTADO el mismo dia: se sospecho que el telefono del coach solo bajaba sesiones al arrancar. No: runSync las baja en cada vuelta a primer plano. Fue un error de lectura del asistente, que imprimio las ultimas 6 de 7 sesiones de una lista ordenada de la mas nueva a la mas vieja.

HECHO en codigo el 21-sep, pendiente del SQL y de probarlo en el telefono de NIKO (necesita release). Se decidio que vaciar todo el historial cuenta igual que borrar una por una: lo que el atleta ya no tiene no puede seguir contandole al coach. SessionSync.toDelete elige lo que llego a subir -lo que esta en el ledger-; WorkoutStore.queueSessionDeletes lo anota como pendiente y lo saca del ledger en la misma escritura, para que una sesion que vuelva con un respaldo suba otra vez; syncSessions manda los borrados ANTES de las subidas. AssignmentRepository.deleteSession llama a delete_session con la clave publica; si la funcion todavia no existe devuelve null y el borrado se queda pendiente hasta que exista. El SQL, en docs/supabase/td-149-delete-session.sql, borra ademas las tres sesiones a medias del 19-sep que ya no estan en el telefono de NIKO.

### Feature

- [ ] **TD-151** El dolor se anota cuando pasa, no al terminar el training
  - LO PIDIO el usuario el 21-sep-2026: el dolor al despertar, los minutos que tarda en aflojar y el dolor de antes se contestan en la pantalla final, despues de una hora de ejercicio, y se vuelve un ejercicio de memoria. "Mientras mas pronto registre el dolor, mejor": el de la manana apenas se despierta, y el alivio apenas pasa.

LO QUE HAY HOY: painOnWaking y painFadeMin (TD-125) viven en SessionLog y se preguntan al final. painBefore se pregunta en la pantalla previa al player ("How is your back right now?"), pero el boton de play de la tarjeta se salta esa pantalla -onPlay = openPlayer + onStart-, asi que en la practica tambien se contesta al final, como "Pain before". painAfter se pregunta al final, que es su momento.

PLAN PROPUESTO:
(1) La manana sale de la sesion. Un registro por dia -fecha, dolor al despertar, hora a la que lo anoto, hora a la que aflojo- en su propio almacen, con respaldo. Se anota desde la lista de trainings, en una tarjeta bajo el calendario que solo aparece si algun training lleva tracksPain. Al despertar: el numero, un toque. Cuando afloja: un boton "It eased", y los minutos los calcula el app con las dos horas -no hay nada que recordar ni que contar-. Hecho, la tarjeta se reduce a una linea editable.
(2) Los dias sin sesion tambien cuentan. Hoy un dia de descanso no deja dolor de la manana; con el registro por dia, si.
(3) La sesion sigue llevando painOnWaking y painFadeMin, copiados del registro del dia al guardarse, para que el historial y lo que lee el coach no cambien de forma. En la pantalla final se ven ya contestados.
(4) El dolor de antes, al arrancar de verdad: si el training lleva tracksPain y no se contesto en la pantalla previa, se pregunta en el player durante el primer ejercicio -la caminata-, en una franja que desaparece al contestar. Sin bloquear nada.

EDGE CASES: se despierta, anota, y no entrena ese dia; entrena dos veces el mismo dia; anota el alivio pero no el despertar; lo anota pasada la medianoche; importar un respaldo sin registros diarios; NIKO no tiene tracksPain y no ve nada de esto.

PROPUESTA DEL USUARIO, el mismo 21-sep, y hacia donde se inclina: una ALARMA propia del app en vez de la tarjeta en la lista. Suena al despertar y la pantalla de la alarma es la pregunta del dolor, asi que se contesta apenas abre los ojos, antes de moverse, que es justo lo que mide painOnWaking. Queda como propuesta, sin decidir. A pensar antes de empezar: permisos de alarma exacta (SCHEDULE_EXACT_ALARM / USE_EXACT_ALARM desde Android 12-14) y de pantalla completa sobre la de bloqueo (USE_FULL_SCREEN_INTENT, restringido desde Android 14); si reemplaza a la alarma que ya usa o convive con ella; que hacer si la apaga sin contestar; y como se enlaza con el "It eased", que seguiria necesitando un sitio -la misma notificacion, que se queda puesta hasta que afloja, es un candidato-.
- [ ] **TD-148** Estimar la duracion de un training que todavia nadie ha corrido
  - LO QUE QUEDO FLOJO de TD-040. La tarjeta usa la mediana de las sesiones reales, pero un training sin historial cae al calculo del motor, que se queda corto -unos 43 minutos frente a los 73 reales de LUMBAR-. Afecta a LUMBAR (short), a las rutinas de NIKO y a cualquiera nueva: dicen un numero bajo hasta que se entrenan una vez.

LA SALIDA, con sus propios datos: el historial ya mide el factor. Sus rutinas tardan del orden de 1.7 veces lo que suma la cola del player, porque entre ejercicios pasan cosas que el motor no ve. Calculando ese factor por telefono -con los trainings que SI tienen historial- y aplicandolo a los que no, un training nuevo estimaria bien desde el primer dia.

SIN DECIDIR: si el factor es uno por telefono o por tipo de rutina (una de fuerza con cambios de disco no se parece a una de movilidad), y cuantas sesiones hacen falta antes de fiarse de el.

Aplazado por el usuario el 20-sep: 'usemos tu estimacion de momento, ya otro dia hacemos que el app lo calcule con mas certeza'.
- [ ] **TD-144** Progresiones: un mismo ejercicio con varios niveles, cada uno con su video
  - PLANTEADO por el usuario el 19-sep-2026, al revisar por que el video no se comporta como un campo mas del ejercicio: 'un ejercicio puede tener progresiones: side plank, con piernas a 90 o estiradas, es un mismo ejercicio que podria tener 2 videos. Imagina pistol squat, puede tener varias progresiones y es el mismo ejercicio, y asi hay varios'.

ES DOMINIO, NO UI. Una progresion es una forma distinta de hacer el MISMO movimiento, mas facil o mas dificil, con su propio video y sus propias instrucciones. El atleta esta en un nivel y sube: para un principiante, progresar ES eso, no anadir peso. Hoy el app no lo sabe expresar y por eso la plancha lateral acabo partida en ex_side_plank_l y ex_side_plank_r -que es otro hack, el de los lados- y la version de rodillas vive dentro del texto de las instrucciones ('si no puedes con las piernas estiradas, apoya las rodillas dobladas').

OJO CON LA PALABRA: 'variante' ya esta tomada en el modelo (WorkoutVariant = el dia A/B de un workout rotativo). Esto es otra cosa y necesita su propio nombre: progresion o nivel.

EL CAMINO BARATO, y probablemente el bueno: cada progresion es una entrada del catalogo (ex_side_plank_knees, ex_side_plank_full) mas un campo de FAMILIA que las agrupa. Asi el video por exerciseId, las instrucciones por exerciseId, la comprobacion de lo que falta al repartir (TD-143) y la publicacion (TD-140) siguen funcionando sin tocar nada: una progresion es un ejercicio con id propio. Lo que aporta la familia es lo que hoy se pierde: que el historial las cuente juntas -pasar de rodillas a piernas estiradas es progreso, no un ejercicio nuevo que empieza de cero- y que el selector las ofrezca agrupadas en vez de como seis entradas sueltas.

LA ALTERNATIVA CARA, descartada de momento: que el ejercicio lleve una referencia propia al video (videoId) y la cache deje de indexarse por movimiento. Resuelve el video pero no el concepto, y el concepto es lo que importa: el coach necesita saber en que nivel esta cada uno, no solo que grabacion se ve.

SIN DECIDIR: si una progresion hereda las instrucciones de su familia o las tiene propias, y que hace el historial cuando alguien baja de nivel.
- [ ] **TD-143** Antes de repartir, el app dice que le va a llegar incompleto
  - PEDIDO el 19-sep-2026, a raiz de encontrarlo pasando de verdad: al verificar TD-139 se cruzaron los ejercicios de 'NIKO 2 · Muay Thai' -el que ella entrena el lunes- contra las instrucciones publicadas, y SEIS estaban sin una sola linea: los cinco del calentamiento (cuerda, rotacion de cadera, 90 a 90, rotacion de hombros, sombra) y el salto de llanta. Se asigno asi, y se descubrio por casualidad la vispera.

POR QUE PASA: repartir parecia un solo acto y no lo es. El training viaja, pero las instrucciones y el video son de cada exerciseId y pueden no existir. Cuando faltan, el fallo es SILENCIOSO: quien recibe abre el ejercicio y ve un nombre, y quien reparte no se entera nunca.

QUE SE HACE: DeliveryCheck.gaps(training, instrucciones, videos publicados) lista los ejercicios que llegarian sin instrucciones o sin video, y el dialogo de 'Assign to' lo ensena ANTES de confirmar, con los que no tienen instrucciones primero y en blanco -sin instrucciones no se puede hacer el ejercicio; sin video se puede leer como se hace-.

AVISA, NO BLOQUEA: repartir algo sin video es normal; repartirlo sin saberlo es lo que no.

EL VIDEO PROPIO NO CUENTA: vive en el directorio privado del telefono que lo asigno y no viaja con la rutina, asi que solo vale el publicado. Mirar el estado del video a secas diria que esta cubierto cuando el otro telefono no puede descargarlo.

Y MIRA LAS VARIANTES: un workout rotativo esconde sus ejercicios dentro de ellas, que en el dia de Muay Thai son casi todo el training. Hay un test que lo fija.
- [ ] **TD-137** Circuitos: alternar ejercicios por rounds en vez de terminar uno para empezar otro
  - LIMITACION DEL MODELO, encontrada al diseñar el dia 6 de NIKO (19-sep-2026). Lo ideal para defensa personal es un circuito: saco 60 s → sprawl 30 s → saco 60 s..., repetido por rounds. El player hace un ejercicio con TODAS sus series y recien pasa al siguiente, asi que un circuito no se puede expresar: hoy va como series seguidas de cada ejercicio.

Es la regla del coach -'lo que se propone tiene que entrar en el modelo'- aplicada: se diseña con lo que hay y se anota lo que falta, como paso con la piramide de McGill (TD-085). Seria un workout con 'rounds': repetir su lista de ejercicios N veces, con descanso entre vueltas.
- [ ] **TD-136** Semana base de NIKO, sembrada un dia a la vez
  - APROBADA el 19-sep-2026 (ver docs/niko.md): seis dias numerados -gluteo pesado, Muay Thai, tren superior, gluteo a una pierna, Muay Thai y potencia, mixto-, de 75 a 90 minutos, con el mismo calentamiento de 5 minutos y rounds de 3 minutos con 1 de descanso.

LA DINAMICA, decision del usuario: 'no quiero pasarle 6 rutinas, solo la siguiente'. Se siembra y se asigna SOLO el dia siguiente, la vispera, y cada dia se ajusta con lo que paso en el anterior. nikoTrainings() lista los dias que ya existen; agregar uno es agregarlo ahi y subir NIKO_REVISION. Numerados y no con nombre de dia: si un dia no puede, al siguiente sigue con el numero que le toca.

EL 19-SEP: revision 4. NIKO 1 pasa a llamarse 'NIKO 1 · Glúteo pesado', gana el calentamiento y la nota de la hiperextension dice 'Banca a 45°' -la banca es regulable y el usuario pidio que ella lo tenga presente-. Se siembra NIKO 2 · Muay Thai para el lunes 21, con dos ejercicios nuevos en el catalogo -saco y giro ruso- y sus instrucciones en español, mas las de la plancha lateral. Los helpers pasan a NikoBlocks, como LumbarBlocks, para que cada dia nuevo sea solo su contenido. Los tests cubren todos los dias: pesos armables, barra de 6 y no la de 20, e instrucciones en español para todo lo que ella no conocia.

MISMO DIA: la plancha lateral de NIKO 2 salia en INGLES en el telefono del coach, porque ahi venia de la rutina lumbar y las del catalogo no pisaban nada. El acepto el cambio para el suyo ('no hay problema de mi parte'): supersededInstructions() reconoce esas versiones como reemplazables. Su puente de la lumbar no entra, porque no se pidio. Y de paso: la plancha y el giro ruso estaban escritos en FEMENINO -'apoyada', 'sentada'- cuando las del catalogo le llegan a todos; pasan a neutro y las viejas tambien se reconocen. Verificado en su telefono, con un test que fija el alcance.
- [ ] **TD-133** El app en español, para NIKO y para cualquiera que no lea ingles
  - PEDIDO el 19-sep-2026: 'niko no maneja el ingles, lo ideal seria que el app tenga soporte para español, apuntalo como un TD'. Para salir del paso ese dia se pusieron en español las instrucciones del catalogo (TD-131) y el texto libre de su rutina (TD-127 revision 3).

LO QUE FALTA: el app es English-only por decision de producto (I18n.get() devuelve EN y lang() es 'en'), pero la base existe: Strings es una clase con todas las cadenas y el catalogo ya tiene los nombres en los dos idiomas. Hace falta un Strings ES completo, un ajuste de idioma por telefono (no por training: el idioma es de quien lo usa), y decidir que pasa con los textos libres que escribe el coach -notas, nombres de bloques-, que no se traducen solos.

OJO: el idioma es del TELEFONO. Si el coach escribe en ingles para si y en español para NIKO, las notas de sus rutinas tienen que ir en el idioma de quien las recibe.
- [ ] **TD-131** Las instrucciones del catalogo viajan con el app, a todos los telefonos
  - ENCONTRADO el 19-sep-2026 al ir a escribir las instrucciones del dia de NIKO: viven en cada telefono y ASIGNAR NO LAS MANDA. Escritas en el telefono del coach, a ella no le habrian llegado nunca. Nacio de su pregunta: 'como se ejecuta el hip abduction, y con el neck isometric estoy aun mas perdido'.

LA SALIDA: 'como se hace un hip thrust' no es de nadie, es del catalogo. MasterDefaults.catalogInstructions() las lleva dentro del app y se siembran en TODOS los telefonos -sin puerta de perfil, y tambien en una instalacion limpia-, solo donde el ejercicio no tiene instrucciones: lo que alguien escribio a mano no se pisa. Van por revision, como las rutinas.

LAS PRIMERAS: hip thrust, peso muerto rumano, abduccion de cadera, bulgara (con la ejecucion de gluteo), hiperextension, cuello isometrico (cada serie es una direccion, en orden), subida al cajon y puente. Para que le lleguen a NIKO hace falta un release: su telefono tiene que actualizar.

EN ESPAÑOL, el mismo 19-sep: 'urge las instrucciones en español, niko no maneja el ingles'. La revision 2 del catalogo las cambia a español. Las inglesas de la revision 1 quedan como catalogInstructionsV1 solo para reconocer las que nadie toco y cambiarlas; lo editado a mano no coincide y se respeta. Un test comprueba que todo ejercicio de la rutina de NIKO tiene instrucciones y que no son las inglesas.

Y UN BUG QUE DESTAPARON, visto en captura el mismo dia: la hoja de instrucciones del player no tenia scroll, y lo que no entraba en la pantalla se cortaba sin aviso. Nunca se habia notado porque las instrucciones eran cortas; las de la abduccion en español fueron las primeras que no entraron, y el paso que quedo partido era justo el que dice como corregirlo si arde donde no toca. Ahora hace scroll y deja libre la barra de navegacion. Se revisaron las otras dos hojas del app -colores y sesiones del dia-: usan listas que ya hacen scroll.
- [ ] **TD-130** El descanso dibuja lo que hay que cargar: discos por lado y mancuernas
  - PEDIDO el 19-sep-2026, con un relato que es el caso de uso entero: 'NEXT: HIP THRUST · 55 KG me fuerza a hacer matematicas en un mal momento: 70 kg - 6 de la barra... 65 / 2 son 32.5 a cada lado, esos son 20 + 10 + 2.5? ... ahora yo soy el rapido haciendo calculos, imagina lo que le va a tomar'. Y trajo el mockup: una barra dibujada con los discos de un lado, del tamaño de su peso, y la barra a la izquierda.

LO QUE HACE: fuera de la serie -preparacion, descanso, enfriamiento- el hueco del player ensena SOLO lo que hay que cargar para la proxima serie. Con barra, la media barra dibujada: el tope con el peso de la barra y los discos de un lado, de mayor a menor, con alturas segun el peso. Con mancuernas, 'una o dos' dibujadas y '1 x 7.5' o '2 x 7.5' -pedido explicito: 'si es una, 1 de 7.5, si son 2 debe decir 2 de 7.5'-. Todo en blanco, porque el fondo del player es el acento. Sale tambien en la PREPARACION, que es donde se carga la primera serie.

LAS PIEZAS: Plates.perSide, pura y con tests sobre el inventario real (cuatro de cada disco, dos por lado; el voraz es exacto con estas denominaciones y cualquier multiplo de 2.5 hasta 155 se arma); Exercise.dumbbellCount (1 o 2; antes DUMBBELL era siempre dos y una sola mancuerna iba como TOTAL, igual que las maquinas); PlayerStep lleva weightType, barWeight y dumbbellCount; y el editor ofrece 'una/dos' con mancuernas.

REVISION 8 DE LA RUTINA LUMBAR: carry y goblet pasan a DUMBBELL con UNA mancuerna. El numero por serie es el mismo, asi que el historial no se parte.

Y UN TEST QUE HACE CUMPLIR EL CHECKLIST: cada peso de las rutinas del coach se arma con el equipo de la casa (HomeGym, desde docs/equipo.md), ninguna barra se queda en el 20 por defecto, y nada va en kilos TOTALES salvo una maquina declarada -que era justo el hueco por donde se colo el error original-.

AJUSTES DE DISEÑO, el mismo 19-sep tras probarlo: (1) la tarjeta de la SERIE dice solo el total -'46 kg'-, sin '6 + 40': la pregunta es por el peso que se movio, y el desglose es trabajo de la tarjeta de carga. (2) La barra se dibuja COMPLETA, con los discos en los dos lados -grandes hacia dentro- y el peso de la barra en el centro; los discos mas delgados (hasta 22dp, y se afinan si hay muchos) y el numero en UN solo lado, vertical, porque en un disco delgado '1.25' no entra horizontal. Fuera el 'per side', que con la barra completa ya no tiene sentido. (3) La mancuerna con cabeza HEXAGONAL, como las de la casa -el trajo el dibujo-, dibujada en Canvas, con el peso en el cuadrado central de UNA cabeza: el mismo numero en las dos se leeria como el doble.

SEGUNDA VUELTA DE LA BARRA, con captura: (1) con 52dp de centro 'parece una mancuerna'; ahora la barra ocupa todo el ancho de la tarjeta y los discos van en los extremos. (2) El numero en los DOS lados -revierte la decision de un solo lado-: se carga un lado y luego el otro, y cada lado tiene que leerse solo. En la mancuerna sigue en una sola cabeza, que es como el lo pidio. (3) Nada despues del ultimo disco: 'termina el disco, termina la barra'. Se quitaron los topes que sobresalian.

VALIDADO por el usuario el 19-sep: la barra -'me gusta'- y la mancuerna hexagonal -'muy bien, validado'-. Ultimo ajuste: el peso de la barra iba en una etiqueta blanca translucida sobre la barra, tambien clara, y 'se ve muy apagado'; paso a etiqueta oscura con numero blanco.
- [ ] **TD-127** Rutina de NIKO orientada a Muay Thai: primer dia sembrado
  - PRIMER DIA de la semana nueva de NIKO (ver docs/niko.md), sembrado en el telefono del COACH para que lo asigne. Quedan cinco dias por escribir: Muay Thai por rounds, tren superior con traccion, gluteo unilateral, Muay Thai con potencia, y el mixto del sabado.

EL DIA A ataca su queja concreta -siente las piernas en el cuadriceps y no en el gluteo- y es bisagra de cadera casi entero. Las tres notas que lo hacen funcionar van DENTRO del app, porque sin ellas es el mismo ejercicio con otro musculo: el puente abre la sesion para que el gluteo se encienda primero, el hip thrust lleva pausa de 2 s arriba, y la bulgara va con el torso inclinado y paso largo.

Cargas: hip thrust 45/55/65/70 -de lo que ya movia, 40/50/60/70 por 10, descontando lo que cuesta la pausa-; rumano 30/35/40/40, conservador porque la bisagra es nueva para ella; bulgara con sus mismas mancuernas pero con otra ejecucion; abduccion con la tobillera de 1 kg que ya tienen.

Cuatro ejercicios nuevos en el catalogo: peso muerto rumano, abduccion de cadera, subida al cajon y cuello isometrico. El de cajon todavia no se usa: es para el dia de gluteo unilateral.

Va por revision propia (NIKO_REVISION), igual que la lumbar: cambiar la rutina es editar la funcion y subir el numero.

REVISION 2, el 19-sep, porque la 1 se entrego a medias -a las 22:40, apurada por la hora-: sin instrucciones para los cuatro ejercicios nuevos, con el hip thrust en kilos TOTALES sin preguntar que barra usa ella, y el rumano asumiendo la barra de 20 por defecto, el mismo error que el 15-sep le costo al otro atleta. El usuario la probo, hizo la cuenta entre series y le salio 71 donde tocaba 70: 70 sobre una barra de 6 son 64 de disco, 32 por lado, y con estos discos no existe. Su resumen: 'no le pusiste cariño'.

Ahora: su barra es la EZ de 6 kg; hip thrust 46/56/66/71 (20, 25, 30 y 32.5 por lado) y rumano 26/31/36/36 (10, 12.5 y 15 por lado), todo armable. Las instrucciones van en el catalogo para que le lleguen (TD-131), y un test comprueba que cada peso de las rutinas del coach se puede armar con el equipo de la casa.

REVISION 3, el 19-sep: lo que es texto libre pasa a español -notas, nombres de bloques y el nombre del training, 'NIKO - Día de glúteo'-, porque ella no lee ingles. Los nombres de los EJERCICIOS siguen en ingles: el player los traduce del catalogo segun el idioma del app, que hoy es fijo en ingles (TD-133).
- [ ] **TD-126** El historial de un atleta asignado sube al coach
  - EL HUECO, planteado el 18-sep-2026 al empezar a disenar la rutina de NIKO: 'lo ideal seria que le lleguen las rutinas en automatico y que puedas leerlo sin necesidad de su telefono'. La primera mitad ya existe -TD-063, TD-067 y TD-068: las asignaciones bajan por Supabase con aviso y deslizar para refrescar-. La segunda no: Supabase lleva perfiles, trainings y asignaciones, todo HACIA ABAJO, y las sesiones nunca salen del telefono.

POR QUE BLOQUEA: al otro atleta se le entrena leyendo su historial -que completo, que salto, con que peso, como se sintio cada serie-. A NIKO habria que entrenarla a ciegas, que es justo lo que se dejo de hacer esta semana. Sin esto, su rutina se ajusta por lo que ella cuente, y contar no es medir.

QUE HARIA FALTA: una tabla de sesiones en Supabase, subida al cerrar una sesion (con reintento, porque el telefono puede estar sin red al terminar), y que el coach pueda leer las de sus atletas. Decidir si sube todo el historial o solo lo de trainings asignados: lo suyo propio puede no ser asunto del coach.

OJO: la service_role key no entra al repo por ninguna razon; esto se resuelve con RLS sobre la anon key, como el resto.

PLAN DE IMPLEMENTACION, en cuatro etapas que se pueden soltar por separado.

ETAPA 1 - La tabla. sessions(id uuid, profile_id text, payload jsonb, completed_at timestamptz, device text, created_at). El payload es el MISMO JSON que ya escribe SessionJson, asi que no hay formato nuevo que mantener: lo que se guarda en el telefono es lo que viaja. RLS: un perfil escribe y lee lo suyo; el coach autenticado lee el de los perfiles que le pertenecen, con la misma politica que ya usan trainings y assignments. Nada de service_role en el repo.

ETAPA 2 - La subida. Al cerrar una sesion, ademas de guardarla en el store, encolarla. Un flag 'uploaded' por sesion y un reintento en cada vuelta a primer plano, porque el telefono puede estar sin red justo al terminar de entrenar -y en un gimnasio en casa eso pasa-. La sesion NUNCA depende de la red para guardarse: primero el disco, la nube despues. Idempotente por el id de sesion, que ya es unico.

ETAPA 3 - La lectura del coach. Una pantalla que liste a sus atletas y abra el historial de cada uno, reutilizando HistoryScreen tal cual: los registros son el mismo ExerciseRecord. Solo lectura -corregir el registro de otro es TD-101 nivel 3-.

ETAPA 4 - Que el asistente lo lea sin el telefono de nadie. Con la 3 hecha, el snapshot del telefono del coach ya trae las sesiones de sus atletas y se lee como hoy. Si hiciera falta leerlo directo de Supabase, va con la anon key y RLS, nunca con service_role.

A DECIDIR ANTES DE EMPEZAR: si sube TODO el historial o solo las sesiones de trainings asignados. Lo segundo es lo defendible -lo que ella entrena por su cuenta no tiene por que ser asunto del coach- y es una condicion barata de escribir. Tambien hay que decidirlo con ella, no solo con quien disena.

LO QUE HABILITA: entrenar a NIKO con datos desde la segunda semana en vez de por lo que cuente. Hoy su rutina se ajusta a ciegas.

DECIDIDO el 19-sep, con el usuario: (1) las sesiones las LEE solo el coach autenticado -llevan el dolor de cada dia- y un telefono de atleta solo puede SUBIR las suyas, por una funcion que no deja leer ni borrar; (2) solo suben las de trainings ASIGNADOS, comprobado en el servidor. El SQL esta en docs/supabase/td-126-sessions.sql y lo corre el usuario una vez en el SQL Editor: el asistente no tiene ni debe tener la clave para crear tablas.

HECHO el 19-sep (v1.0.282), etapas 1, 2 y 4 del plan. El usuario corrio el SQL y se verifico con la clave publica sin escribir nada: la funcion rechaza un training no asignado (false), la tabla no se deja leer sin sesion de coach, y escribir directo da 401 por RLS.

EN EL APP: SessionSync (puro, con tests) decide que sube -solo trainings asignados, y solo lo que cambio desde la ultima subida, con una huella por sesion en un ledger- y como se leen las filas. AssignmentRepository.uploadSession llama a upload_session con la clave publica; athleteSessions baja las de todos con la sesion del coach. El ViewModel lo corre al arrancar, al terminar una sesion, al completar el dolor o el feedback, y en cada sincronizacion; una sola vez a la vez. Las sesiones bajadas van a un almacen APARTE -no al historial del coach, que contaria entrenamientos que no hizo- y al respaldo, formato 3, bajo athleteSessions: por ahi las lee el asistente.

FALTA: la etapa 3, una pantalla para que el coach vea en el app el historial de cada atleta. Y la prueba de punta a punta, que necesita que el telefono de NIKO tenga esta version -release- y que entrene un training asignado. Primera oportunidad: NIKO 2, el lunes 21.

UNA CAIDA EN EL CAMINO, el mismo 19-sep: la v1.0.283 no arrancaba -NullPointerException en un hilo de fondo, en bucle-. El init llama a refreshSessions -> syncSessions, que lanza un hilo que usa el candado de subida, y ese candado estaba declarado MAS ABAJO que el init: Kotlin inicializa en orden de aparicion y el hilo lo encontraba en null. Era una carrera: con la v1.0.282 no se cayo. Arreglado en la v1.0.284 declarandolo antes del init, verificado en el telefono -cero caidas, datos intactos- y anotado como regla en AGENTS.md. No llego a publicarse.

ETAPA 3, hecha en codigo el 22-sep, pendiente de validar: el historial de un atleta en el app del coach. Se entra por Settings -> Coach -> People -> tocar el nombre -> "History · N sessions". Es la MISMA pantalla que el historial propio, con sus sesiones (AthleteHistory: solo las suyas, sin repetidas, de la mas nueva a la mas vieja) y sin borrar: el registro es de el o ella. El historial de cada ejercicio tambien lee las suyas. Al abrirlo se baja lo ultimo. Las sesiones bajadas viven ahora tambien en memoria (athleteSessions, antes del init) para que la pantalla se actualice sola. De paso, cada serie del historial ensena lo que salio contra lo planeado -"6/8 reps", "18s/25s"- y el icono de como fue en lo que no lleva peso (TD-152). La prueba de punta a punta de TD-126 ya se dio el 21-sep con NIKO 2.
- [ ] **TD-125** Medir el dolor al despertar, que es el que lleva anios
  - DE LA CONVERSACION del 18-sep-2026 sobre el 'dolor normalizado' (ver docs/coach-log.md). Todos los dias amanece con dolor en la zona lumbar, nivel 3, que se va en 10-15 minutos de moverse y lleva anios. Nunca se habia medido.

POR QUE IMPORTA MAS QUE LO QUE YA SE MEDIA: entrena unos veinte minutos despues de levantarse, asi que el painBefore que registra ya esta tomado cuando el dolor de la maniana se fue casi entero. La serie del historial -4, 4, 3, 2, 1- empezaba a contar despues de lo que hay que medir, y el 0 del 18-sep no era un 0: era 'nada fuera de lo que para mi ya es normal'.

DOS CAMPOS en SessionLog: painOnWaking (la misma escala DVPRS, al minuto 0, sentado en la cama) y painFadeMin (minutos hasta que aflojo). Se preguntan AL TERMINAR, junto a 'como te fue', y van primero en esa tarjeta porque son el dolor que se quiere mover. No se preguntan antes de empezar: la pantalla de arranque tiene que dejar darle Start, que a esa hora es lo unico que se quiere hacer -lo eligio el usuario-.

Los minutos son opciones sueltas (0, 5, 10, 15, 20, 30, 45, 60+) y no un contador: nadie mide esto con cronometro, se dice 'unos quince'. El 45 y el 60 existen a proposito, porque una rigidez de mas de 45 minutos seria otro patron.
- [ ] **TD-124** La caminata registra a que velocidad fue
  - LA VELOCIDAD ERA LA VARIABLE MAS IMPORTANTE Y LA UNICA SIN REGISTRAR. En cuatro sesiones fue 3, 4, 5 y 6 km/h y la respuesta del dolor mejoro con cada subida; dos veces se concluyo 'la caminata es neutra' con el dato bien y la interpretacion mal, porque nadie preguntaba la velocidad. Vivia dentro de la nota -'6 km/h, arms loose'-, o sea texto que nadie puede comparar entre sesiones, y habia que preguntarla por chat.

SE HIZO ESTO Y NO TD-095 (modo distancia). Se evaluaron los dos: el modo distancia toca 46 sitios del enum WorkMode mas motor, player, editor e historial, y lo que arregla es que el carry deje de decir '2 REPS' -cosmetico, porque la distancia es fija, 36 m, y ya esta escrita en docs/equipo.md-. La velocidad es mas chica, usa el patron de la tarjeta del peso que el usuario ya sabe usar, y da el dato que se venia pidiendo a mano. TD-095 sigue abierto para cuando una rutina lleve metros que cambien.

COMO QUEDO: Exercise.speedKmh (null = el ejercicio no se dosifica asi), SetRecord.speedKmh, y en el player una tarjeta con - y + en pasos de 0.5 en el mismo sitio que la del peso. Lo que enseñe al terminar la serie es lo que se registra, se haya tocado o no: si camino a lo prescrito, eso es lo que hizo. El editor lo ofrece en los ejercicios por tiempo, y por debajo del minimo se apaga como null en vez de quedar en cero. El historial lo dice al lado del tiempo: '12:00 - 6 km/h'.

REVISION 7 DE LA RUTINA: las tres caminatas pasan la velocidad de la nota al campo. Entrada y corta a 6 km/h, que es lo que ya camina. La de cierre queda en 4 km/h, y ese numero LO PUSO EL ASISTENTE: la nota decia 'easy' y nunca se pregunto a que velocidad la hace. Si no es 4, se corrige con el - y el + y queda registrado.
- [ ] **TD-122** El feedback del peso se pregunta en el descanso, y los huecos se cierran al terminar
  - EN SU NOTA de la sesion del 18-sep-2026: 'El feedback del peso se me olvida marcar, no se si ponerlo obligatorio'.

NO SE PONE OBLIGATORIO, por la misma razon que la pregunta del dolor no bloquea el arranque: una pregunta que estorba para seguir entrenando se contesta de cualquier forma con tal de pasar, y ese dato vale menos que ninguno. El problema no es que falte obligacion, es que se preguntaba en el peor momento.

1. LA TARJETA TAMBIEN EN EL DESCANSO. Antes solo salia durante la serie, que es cuando hay que hacer la serie. En el descanso se esta parado esperando. En el descanso la tarjeta habla de la serie que acaba de pasar.

2. LOS HUECOS SE CIERRAN AL TERMINAR. La pantalla de resumen lista las series con peso que quedaron sin marcar y deja contestarlas de un toque. Hace falta ademas porque la ULTIMA serie de un ejercicio no lleva descanso detras: sin esto no hay ningun momento para contestarla, y es justo la que decide si el ejercicio sube.

Marcar desde el resumen escribe en la sesion ya guardada y NO la pasa a EDITED: lo anota el propio usuario, minutos despues y sobre lo que acaba de hacer.

CORREGIDO EL 19-SEP, tras usarlo una sesion: la tarjeta del feedback en el DESCANSO confundia. En el hip thrust ponia arriba '45 kg · How did the weight feel?' -la serie que paso- y abajo 'NEXT: 55 KG' -la que viene-, y no se sabia de quien era el feedback ni cuanto cargar. Sus palabras: 'el feedback de quien es? a cuanto tengo que cargar la barra?'. Y ademas no hacia falta: 'si me olvido de marcar, puedo regresar y marcarlo sin problema'. Se quito del descanso: el feedback vive solo en la serie, y el descanso ensena solo lo que hay que cargar (TD-130). La lista del resumen al terminar se queda mientras el no diga lo contrario.
- [ ] **TD-123** Revision 6 de la rutina: sube el bloque de cadera entero
  - CAMBIO DE PAUTA del 18-sep-2026 (ver docs/coach-log.md). Puente 6/16/31 -> 6/21/36, carry 7.5/10/12.5 -> 10/12.5/15, sentadilla 10/12.5/15 -> 12.5/15/17.5.

La senal viene de dos sesiones: el 17-sep conto 'ligero' en las tres series de carry y sentadilla y en las dos primeras del puente, y el 18-sep marco en el player los 31 kg del puente como LIGEROS. En el puente sube tambien la intermedia porque de 16 a 36 el salto quedaba largo para una serie de entrada.

Suben los tres ejercicios a la vez, que es tres cambios en un dia y se hace a sabiendas: la senal es la misma en los tres y ya se repitio. Si el domingo algo no cuadra, el sospechoso es el bloque como unidad.
- [ ] **TD-118** El historial en corto: la serie en una linea y el feedback en icono
  - PEDIDO el 17-sep-2026, al ver el feedback por serie de TD-117 en el historial: 'en vez de Too light me gustaria que fueramos mas de iconos, porque siento que estamos cargando mucho con texto'. Se le propusieron dos niveles y eligio los dos.

A. EL FEEDBACK EN ICONO: flecha arriba ligero, check justo, flecha abajo pesado. Son las mismas flechas que los botones del player, que es donde se aprende que significan; en el historial solo se reconoce lo que se toco. Las palabras quedan como descripcion de accesibilidad.

B. LA LINEA DE LA SERIE, COMPACTA: de 'Set 1 - 12 reps - 6 kg' a '1   12 x 6 kg'. El 'Set', los puntos y el 'reps' se repetian en cada fila sin decir nada que la columna no dijera ya. En las series por tiempo ya no sale el numero de reps, que ahi no mide nada -en McGill decia '10 reps - 10s' en cada uno de los doce aguantes-, y el tiempo sale con fmtSec (6:00 y no 360s).

Los botones del PLAYER se quedan con texto a proposito: ahi se decide, y el texto es lo que enseña que significa cada flecha. Si con los dias le sobra, se quita tambien.

Las dos pantallas de historial pintaban la serie cada una a su manera; ahora comparten SetLine y setSummary en MasterComponents, con su test.

LOS COLORES, pedidos en la misma sesion y con su sentido: 'flecha arriba verde, luz verde para subir en peso o dificultad; check ambar, estas repitiendo el peso o la dificultad, es como una advertencia, ese ambar no deberia permanecer asi por meses, es una senal de que hay algo que modificar en el ejercicio; flecha abajo rojo, mucho peso, muy dificil'. Quedan como FEEL_UP, FEEL_STEADY y FEEL_DOWN en Color.kt, fijos y no del acento, con ese texto como documentacion.

IDEA QUE SALE DE AHI, sin hacer: si el ambar sostenido es la señal de un ejercicio estancado, el historial por ejercicio podria avisar cuando un ejercicio lleva N sesiones seguidas en 'justo' sin cambiar de carga.
- [ ] **TD-116** El control NEXT dice el peso de la siguiente serie
  - PEDIDO el 16-sep-2026: 'para los ejercicios con peso, en el control NEXT indicame el peso. Resulta valioso entre series saber que peso ir ajustando, porque como esta actualmente tengo que esperar que pasen los tiempos para ver: ah, eran x kilos que tenia que ponerle'.

EL PROBLEMA: el peso solo se veia al empezar la serie, y para entonces ya no sirve. El momento util es el DESCANSO, que es cuando se cambian los discos; hasta ahora habia que esperar a que el descanso terminara para enterarse de cuanto habia que poner.

EL CAMBIO: la etiqueta 'NEXT: <ejercicio>' pasa a decir 'NEXT: GLUTE BRIDGE - 16 KG', con el peso en el color de acento para que se lea de un vistazo. Solo aparece cuando el siguiente trabajo lleva peso (weightTotal > 0), asi que en el resto de la rutina la etiqueta es la de siempre.

Sale del peso del SIGUIENTE paso de trabajo, que es el que ya calcula StepEngine serie a serie, asi que en una piramide como la del puente -6, 16, 31- cada descanso enseña el numero de la serie que viene, no el del ejercicio.

EL COLOR, corregido en la misma sesion. Primero se puso el peso en el color de acento y quedo peor: 'el color no quedo bien'. La razon es que EL FONDO DEL PLAYER ES EL ACENTO -el paso de trabajo pinta la pantalla entera de ese color-, asi que el numero se hundia en el fondo. Y como cada training trae su propio acento, cualquier color elegido chocaria con alguno.

Ahora destaca por BRILLO: el peso en blanco puro y negrita, y el resto de la linea en blanco al 72%. El contraste de luminosidad funciona sobre cualquier fondo saturado, que es lo unico que se puede dar por hecho en esta pantalla.
- [ ] **TD-112** Revision 4 de la rutina: el puente sube a 31 kg y la caminata corta lleva su velocidad
  - CAMBIO DE PAUTA, no de codigo: la rutina sube una revision porque el cuerpo lo pidio, y eso es lo que TD-103 dejo barato. Sale de la sesion del 16-sep-2026 (ver docs/coach-log.md).

1. PUENTE DE GLUTEOS 6/16/26 -> 6/16/31. Discos 0/10/25 sobre la barra de 6 kg; la serie de arriba son 12.5 kg por lado. Los 26 'se sintieron normal' y pidio +5 con estas palabras: 'quiero ir con prudencia'. La serie de barra sola y la intermedia no se tocan: lo que sube es una sola serie de un solo ejercicio.

2. LA CAMINATA CORTA PASA DE 5 A 6 KM/H en la nota. No es subir la dosis, es escribir la que ya corrio: ese dia empezo a 5, a los dos minutos le parecio ligero y subio a 6. Cuarta velocidad en cuatro sesiones (3, 4, 5, 6) y ninguna le ha costado nada.

LO QUE NO SE TOCA Y POR QUE: McGill sigue en 6/4/2. El dolor acaba de romper la meseta -3 -> 1, y el 3 de arranque es el dato bueno- y subir volumen el dia siguiente es apostar contra lo unico que el ha dicho que le importa: no recaer. Un cambio a la vez.

EL TEST DE LA VELOCIDAD SE REESCRIBIO en vez de clavarlo al 6: ahora comprueba que la nota lleva un NUMERO y no un adjetivo, que es la regla de verdad. Clavarlo a una velocidad concreta convertiria cada ajuste de dosis en un test roto.
- [ ] **TD-111** El historial no distingue la pauta de lo que de verdad movio
  - EL HUECO, destapado el 16-sep-2026 al leer la sesion del telefono. El historial guardo 'puente de gluteos 12 x 26 kg' y el coach no pudo saber si eso era lo que MOVIO o lo que el app le PROPUSO: el player escribe en el registro el peso que estaba en pantalla, y si el usuario no toca el dial, la pauta y lo hecho son el mismo numero. La vispera la diferencia era real -la pauta pedia 26 y se quedo en 21 porque le parecio mucho- y solo se supo porque lo conto por chat.

POR QUE IMPORTA MAS DE LO QUE PARECE: la unica razon de ser de este historial es que el coach pueda leerlo SIN preguntar. Un campo que puede significar dos cosas obliga a una pregunta por sesion, que es exactamente lo que se queria quitar. Y al reves: cuando el usuario SI baja la carga, ese es el dato mas valioso del dia -dice donde esta el limite- y hoy se pierde salvo que lo mencione.

QUE HARIA FALTA: que SetRecord recuerde ademas el numero prescrito, y que el historial marque la serie cuando lo hecho y lo pautado no coinciden (una flecha, un color). Con eso 'cumplio la pauta' y 'no la toco' dejan de verse igual, y el coach ve de un vistazo donde el cuerpo dijo que no.

OJO CON EL CASO DE HOY: 16-sep, puente 6/16/26. El usuario confirmo por chat que si los movio y que 26 se sintio normal. Ese registro es correcto; lo que falta es poder saberlo sin preguntar.
- [ ] **TD-101** Control sobre el historial: corregir, anotar y saber de donde salio cada registro
  - PLANTEADO POR EL USUARIO el 14-sep-2026, al leer que dos sesiones se quedaban con el orden equivocado: 'deberias tener control total sobre los registros, eso de que se quedan como estan me suena a que no tenemos control sobre algo que nosotros mismos estamos creando'. Tiene razon. DONDE ESTAMOS HOY. El historial es de **una sola escritura**: lo escribe el player al terminar y despues solo se puede borrar la sesion entera. No hay forma de corregir un dato, ni de anotar nada, ni de reparar algo que se guardo mal. Lo unico que existe es lo que se ha estado haciendo a mano: leer el respaldo del telefono por adb y escribir migraciones de codigo (TD-087, TD-090, TD-091, TD-098). Funciona, pero cada correccion cuesta un build y depende de que el usuario abra el app. EL PROBLEMA MAS SERIO, y lo introdujo el asistente: tras TD-090 el historial tiene una sesion que el player nunca midio -la del 13-sep, reconstruida desde la rutina- y es **indistinguible** de las medidas. Dentro de seis semanas nadie va a saber si esos 12 aguantes se cronometraron o se dedujeron. Si encima se empiezan a corregir registros, el historial deja de ser un registro y pasa a ser una opinion. LO QUE HAY QUE RESOLVER, en tres niveles y por ese orden: (1) ORIGEN. SessionLog gana de donde sale cada registro -medido por el player, reconstruido, o editado a mano-, y el historial lo ensena. Es lo primero porque sin eso lo demas hace dano. Barato: un campo, su serializacion y una marca en la UI. (2) CORREGIR Y ANOTAR desde el app: cambiar reps o peso de una serie mal registrada, quitar un ejercicio que no se hizo, y la nota de como se sintio (TD-089, que es la otra mitad de esto). Le da el control a EL, que es quien estuvo ahi. (3) ESCRITURA REMOTA. Que el historial viaje por el mismo canal que las asignaciones (TD-063/TD-066) para poder leerlo y repararlo sin un build de por medio. Es el unico nivel que da 'control total' de verdad, y es el caro: toca Supabase, identidad y conflictos entre dispositivo y servidor. RECOMENDACION: hacer (1) ya -es pequeno y es el que protege la integridad de todo lo demas-, (2) junto con TD-089, y (3) solo cuando el ciclo de coach lo pida de verdad. EL PRINCIPIO que conviene fijar antes de escribir codigo: el historial tiene que ser **corregible pero auditable**. Que se pueda arreglar un dato malo, y que nunca se pueda confundir lo que se midio con lo que se dedujo.
- [ ] **TD-097** Caminata de cierre en la variante de dia malo
  - PENDIENTE DE DECIDIR, ofrecido al usuario el 13-sep-2026 y todavia sin respuesta. LUMBAR (bad day) (TD-088) termina en el bloque de cadera y gluteo, sin la caminata de cierre de 5 minutos que si tiene el training normal. Se implemento asi porque es exactamente lo que el usuario aprobo -'movilidad, caminata de 6 min, McGill, y cadera al final'- y agregar lo que no se propuso es como se cuelan cambios que nadie pidio. EL ARGUMENTO A FAVOR: terminar una sesion caminando suave es lo que dice el PDF y en una crisis tiene sentido, sobre todo si el bloque de cadera se salta con el skip y la sesion acabaria de golpe. EL ARGUMENTO EN CONTRA: en un dia malo lo que sobra es tiempo de pie, y el propio dato del 13-sep dice que la caminata no fue lo que le hizo efecto. Es una linea de codigo en lumbarBadDayTraining(): un b.walk(...) mas al final. Pendiente solo de que el usuario elija.
- [ ] **TD-096** Poder borrar un ejercicio propio desde la app
  - EL HUECO, encontrado al hacer TD-087: **el app sabe crear ejercicios propios y no sabe borrarlos**. No hay UI, no hay metodo en MasterViewModel, no hay nada: addCustomExercise() los agrega y ahi se quedan para siempre. LA CONSECUENCIA: cada intento de armar algo a mano deja basura en el selector de ejercicios para siempre. Al usuario le quedaron cinco del intento de armar la rutina lumbar y hubo que borrarlos con una migracion de codigo (TD-087), que es una respuesta desproporcionada para algo que deberia ser un swipe. LO QUE HAY QUE RESOLVER, y por eso no es solo agregar un boton: que pasa con un ejercicio propio que SI esta usado en algun training. Borrarlo dejaria a esas instancias sin nombre de catalogo y el player ensenaria el id crudo (ex_curl_up). Opciones: no dejar borrarlo y decir donde se usa; borrarlo y congelar el nombre en cada instancia, que es lo que ya hace ExerciseCatalog.display() con su fallback; o pedir confirmacion nombrando los trainings afectados. La segunda es la que encaja con como esta hecho hoy. Tambien hay que decidir que pasa con su video propio y sus instrucciones, que viven por exerciseId. Relacionado con TD-010 (catalogo expandido) y con el swipe-to-reveal que ya usan las otras listas (TD-039, TD-057).
- [ ] **TD-095** Modo distancia: ejercicios que se miden en metros
  - EL HUECO: WorkMode solo sabe de TIME y REPS. Un ejercicio que se mide en metros no se puede expresar. EL CASO QUE LO PIDIO: el suitcase carry de la rutina lumbar son '3 x 30-40 m por lado' y quedo como 2 repeticiones con avance manual y la nota 'One trip of 30-40 m per side' al lado. Funciona porque el usuario sabe lo que tiene que hacer, pero el player ensena '2 REPS', que no es lo que esta haciendo, y el historial guarda 2 reps, que no sirve para ver progreso: la carga real de ese ejercicio es el peso y los metros. DONDE TOCA: WorkMode gana DISTANCE; StepEngine trata el paso como manual igual que REPS; PlayerStep necesita llevar la distancia y su unidad; el editor, un campo mas; SetRecord, donde anotarla; y el historial, como ensenarla. Es mas ancho que TD-085 porque toca la UI del player, no solo el motor. A DECIDIR: si la unidad es fija (metros) o configurable, y si la distancia va por serie -como quedaron el trabajo y el descanso en TD-085- o es una sola del ejercicio. NO ES URGENTE: con la nota al lado la rutina se hace igual. Sube de prioridad en cuanto una rutina incluya caminatas o carries medidos, que es probable en cuanto el bloque de cadera crezca.
- [ ] **TD-094** Publicar los videos de los nueve ejercicios del lumbar
  - LOS NUEVE MOVIMIENTOS que entraron al catalogo con TD-086 -ex_walk, ex_hip_hinge, ex_curl_up, ex_side_plank_l, ex_side_plank_r, ex_bird_dog, ex_glute_bridge, ex_suitcase_carry, ex_box_squat- no tienen video. El gato-camello si: usa ex_cat_cow, que ya estaba publicado desde TD-062. EL CAMINO, que es el unico automatizable y esta documentado en AGENTS.md ('Como poner contenido en el telefono'): renombrar cada mp4 a <exerciseId>.mp4, subirlo con 'gh release upload videos', agregar su entrada a videos.json con file, rev y bytes, y hacer push de videos.json a main. El app lee el manifiesto de GitHub raw y baja cada video la primera vez que hace falta. **No hay que publicar version nueva del app.** PENDIENTE DEL USUARIO: pasar los archivos. Los que ya estan publicados pesan entre 1.7 y 4.8 MB, que es la escala que conviene. DETALLE UTIL: si solo hay un clip de plancha lateral, las entradas de ex_side_plank_l y ex_side_plank_r pueden apuntar al mismo archivo; el app lo baja una vez por cada id. OJO: el push de videos.json necesita autorizacion explicita del usuario, como cualquier push.
- [ ] **TD-091** Las indicaciones de la caminata dicen de que protege la regla de la primera hora
  - EL PROBLEMA: las indicaciones sembradas decian 'Do not train in the first hour after waking up... Let 60-90 minutes pass'. Leido a las seis de la manana por alguien que ese dia NO tiene margen -el usuario aviso que por trabajo tiene que levantarse y entrenar de inmediato- eso se entiende como 'hoy no entrenes', que es justo lo contrario de lo que conviene en una crisis lumbar. LO QUE DICE AHORA: que lo que cobra caro en esa ventana es la FLEXION LUMBAR CON CARGA y no el movimiento; que la movilidad, la caminata y los tres de McGill entran -el curl-up esta disenado para que la lumbar no se aplane, por eso las manos van debajo-; que esperar 60-90 minutos sigue siendo mejor cuando se puede; y que si entrena recien levantado, el suitcase carry es el unico que toca saltar o aligerar, por ser el unico con carga externa. COMO LLEGA AL DISPOSITIVO: la siembra normal de instrucciones hace merge y NO pisa lo que ya existe -para no borrar lo que escriba el usuario-, asi que hace falta una migracion propia. MasterViewModel.updateWalkInstructions() reescribe la clave ex_walk una sola vez, detras de WorkoutStore.isWalkNoteUpdated(), y **solo si el texto sigue siendo palabra por palabra el que se sembro** (MasterDefaults.WALK_INSTRUCTIONS_V1). Si el usuario lo edito, se queda con el suyo. VERIFICACION: LumbarTrainingTest cubre que el texto nuevo nombra la flexion y el suitcase carry, y que el viejo se conserva aparte para poder distinguirlos. En el dispositivo: abrir las instrucciones de Walk y ver cinco pasos.
- [ ] **TD-089** Registrar como se sintio la sesion, no solo que series se hicieron
  - EL HUECO: el historial guarda que se entreno (SessionLog: series completadas, saltadas, peso, duracion) pero no COMO se sintio, que es el dato con el que se ajusta una rutina de rehabilitacion. Hoy lo unico parecido es el feedback de peso por ejercicio (feedbackDeltaKg, 'How did the weight feel?'), que no aplica a un training sin carga. POR QUE IMPORTA AHORA: el 13-sep-2026 el usuario paso a pedir que el asistente le arme y ajuste las rutinas (ver docs/coach.md). Ese ciclo se alimenta de dos cosas: lo que registra el app y lo que el cuenta. Lo segundo hoy vive solo en la conversacion, y una conversacion no se consulta dentro de seis semanas. QUE HARIA FALTA, minimo: al terminar una sesion, dolor antes y despues (0-10) y donde se siente -centrado en la espalda o irradiado a la pierna-. Esa segunda pregunta no es cosmetica: que el dolor se centralice o se irradie es lo que decide si un ejercicio sigue en la rutina. Opcionalmente, una nota libre por sesion. A DECIDIR ANTES DE IMPLEMENTAR: si va en SessionLog (viaja con el respaldo y con el historial, y hay que versionar SessionJson) o en un registro aparte; si se pregunta siempre o solo en los trainings marcados; y como se ensena despues, porque un numero por sesion sin una linea de tiempo al lado no dice nada. No confundir con TD-042 (resumen de stats en Historial), que es presentacion de lo que ya se guarda. HECHO en v1.0.246, nivel 1: se pregunta AL TERMINAR, en la pantalla de fin de sesion del player, junto a las sugerencias de peso. Cuatro cosas: dolor antes (0-10), dolor ahora (0-10), donde -espalda o pierna- y una nota libre. DOS DECISIONES QUE VALE LA PENA NO PERDER. (1) Se guarda EN CUANTO toca cada cosa, sin boton de enviar, porque el usuario pidio que fuera en caliente: 'despues se vuelve un ejercicio de memoria y muchas veces falla'. Un formulario que hay que confirmar es una ocasion mas de olvidarse, y media respuesta guardada vale mas que una completa que no llego. (2) Null no es cero: no contestar y 'no me dolio' son cosas distintas, y el JSON las distingue porque lo que no se contesta no se escribe. Los numeros van en rejilla de once botones y no en un stepper: llegar a 4 son cuatro toques con el stepper y uno aqui, y esto se contesta de pie y sudando. Solo pregunta en los trainings que lo piden: Training.tracksPain, encendido en los dos lumbares desde el codigo. La pregunta es sobre dolor y no pinta despues de un cardio. El editor todavia no ofrece ese interruptor. LO QUE FALTA, y es el nivel 2: preguntar el dolor AL EMPEZAR. Al final sigue siendo memoria aunque sea de hace media hora, que es justo lo que el usuario queria evitar. Lo correcto es un toque antes de arrancar el player, guardarlo y prellenar con eso la pantalla del final. Se dejo fuera para entregar esta noche algo usable manana.
- [ ] **TD-084** Los videos viajan con la asignacion, sin asignarlos a mano
  - EL PROBLEMA, planteado por el usuario: confecciona un training de 30 ejercicios para NIKO, se lo asigna, y en el telefono de ella aparece el training SIN videos. Asignar envia el training, no los medios. Sus palabras: 'asignarlos uno a uno no es opcion, no quiero pasar el tormento de volver a asignar 30 veces un video y con el riesgo de error que eso implica'. POR QUE COLUMNA SI SE VE Y ON YOUR MARKS NO: hay dos caminos y solo uno viaja. El manifiesto (videos.json en GitHub) deja el fichero en repo/ y cualquier telefono lo baja solo; el selector del app lo deja en own/, que es privado del dispositivo. Los siete ejercicios de COLUMNA estan publicados en el manifiesto desde TD-062; los de On Your Marks entraron uno a uno por el selector. LO QUE YA ESTA HECHO Y NO HAY QUE TOCAR: VideoRepository con cola, prioridad urgente, revisiones, cache y reintentos; VideoCache con own/ ganando sobre repo/; descarga solo por wifi salvo que se active lo contrario; Http.request ya acepta cabeceras; Supabase.headers ya las construye; AuthStore da token con refresco. Y el punto de pivote estaba anotado desde el principio en VideoRepository: 'un solo sitio a proposito, TD-063 lo cambia por uno por usuario tocando esta constante y nada mas'. LA RESTRICCION QUE LO CONDICIONA TODO: el dispositivo que recibe NO TIENE CUENTA. Solo el entrenador inicia sesion; los demas eligen su nombre de una lista y leen con la clave publicable. Eso descarta el bucket privado con autenticacion por dispositivo, porque el telefono de NIKO no tiene con que autenticarse. LA SALIDA son URLs firmadas: el bucket sigue privado, el entrenador genera al publicar una URL que lleva su propio permiso incrustado, y el que recibe descarga sin credenciales. Downloader.download no hay que tocarlo, porque la firma va en la URL y no en una cabecera. HASTA DONDE LLEGA LA PRIVACIDAD, dicho al usuario: quien tenga la URL descarga mientras la firma viva, que es el mismo nivel que ya tienen los trainings asignados con la clave publicable. No lo empeora ni lo arregla. FASES: A) tabla videos en Supabase leida con la clave publicable, y VideoRepository la lee con Http.request en vez de Downloader.fetchText; al acabar, COLUMNA sigue igual pero por el camino nuevo, que es la prueba de que el pivote no rompio nada. B) script de PC que sube una carpeta de <exerciseId>.mp4 a un bucket privado, genera las firmas y escribe las filas; va en la PC porque service_role no puede vivir en un APK, y enlaza con TD-066. C) caducidad: expiracion larga, y ante un 403 releer el manifiesto una vez antes de rendirse. D) opcional y fuera de alcance: publicar desde el telefono, que necesita subida binaria y hoy Http.request manda texto. RIESGOS: cuota de Storage (30 videos a ~5MB son ~150MB contra el ~1GB del plan gratuito); datos de quien recibe, ya cubierto por downloadsAllowed pero conviene comprobarlo en su telefono; y los ids custom_1770... de seis de los diez de On Your Marks, que funcionan porque la asignacion los transporta pero para contenido publicado deberian ser ids de catalogo, A DECIDIR ANTES DE LA FASE B porque resubir obliga a rehacer.
- [ ] **TD-081** Unificar los tres iconos de la franja del player (instrucciones, editar y video)
  - NOTA DEL USUARIO, sin decidir todavia: 'no me convence del todo un boton para instrucciones y otro boton para editar el ejercicio, creo que deberiamos unificarlo o pensar en algo mas'. Hoy la franja de rutina lleva dos iconos sueltos a la derecha: InstructionsButton (lista, abre un ModalBottomSheet con los pasos numerados; no se dibuja si el ejercicio no tiene instrucciones) y EditExerciseButton (lapiz, llama a editRunningExercise; no se dibuja si el training viene asignado). O sea que segun el ejercicio y el training puede haber dos iconos, uno o ninguno, y la franja cambia de contenido sin que el usuario sepa por que. A PENSAR: un solo punto de entrada -menu, sheet unico con pestanas, o accion contextual- en vez de dos iconos que compiten por la misma esquina. Contexto: surge tras descartar las instrucciones como relleno del hueco del video (TD-080), que las devuelve a vivir detras de su boton. Relacionado con TD-071, que busca llegar al video y a las instrucciones de un training asignado sin duplicarlo.

AL DIA el 20-sep-2026: ya son TRES. El asistente anadio el de apagar el video (TD-146) porque hacerlo desde el editor costaba cuatro toques, y el usuario lo noto en el acto: 'unificar los tres iconos de la franja del player, ¿lo tienes registrado en un td?'.

Y son tres con TRES reglas de visibilidad distintas, que es lo que de verdad ensucia:
  - instrucciones (lista): no se dibuja si el ejercicio no tiene instrucciones;
  - editar (lapiz): no se dibuja si el training viene asignado;
  - video (camara): se dibuja SIEMPRE, en gris cuando no hay video, justamente para que los otros dos no se muevan de sitio al cambiar de ejercicio.

O sea que la franja puede tener uno, dos o tres iconos segun el ejercicio y el training, y las tres reglas se contradicen entre si. Cualquier unificacion tiene que decidir PRIMERO una sola regla de visibilidad; el numero de iconos es el sintoma.
- [ ] **TD-077** Como reproduce Freeletics sus videos: zoom, loop parcial y velocidad
  - NOTA PARA INVESTIGAR, no hay nada que implementar todavia. Al ver los videos extraidos de Freeletics (38 mp4 1080x1920, 8.8 a 43.7 s, sin cifrar en /sdcard/Android/data/com.freeletics.lite/files/Downloads) el usuario observa que la app NO se limita a reproducirlos enteros en bucle: a unos les hace zoom, a otros les repite solo un tramo concreto, y en algunos cambia la velocidad de reproduccion. O sea que junto al archivo hay parametros de presentacion por ejercicio -recorte, region de interes, tramo del bucle, velocidad- que aqui no tenemos: MASTER hoy reproduce el archivo entero en bucle a 1x (ui/VideoLoop.kt, VideoLoop/ExerciseVideo). El mapa hash->ejercicio y esos parametros viven en /data/data/com.freeletics.lite, que no es accesible sin root y con el APK no debuggable run-as tampoco vale, asi que no se pudo confirmar como los guardan. A DECIDIR MAS ADELANTE si a MASTER le interesa algo de eso; lo mas barato y lo que mas se notaria seria el tramo de bucle (in/out por ejercicio) para que un clip largo no obligue a ver la entrada y la salida cada vuelta. Contexto: se genero al armar el training de prueba On Your Marks.
- [ ] **TD-076** Volumen de los pitidos ajustable en Ajustes
  - El usuario oye los pitidos 'ligeramente mas alto que la musica' y quiere bajarlos un poco; recordaba que eso tenia un porcentaje. LO QUE HABIA: los pitidos de la corrida (AlarmPlayer.beepTone, desde WorkoutPlayerService playBeep y alarmCue) no aplicaban NINGUN volumen, asi que sonaban al 100 % del volumen multimedia, el mismo canal que Spotify. La curva perceptual en dB de AlarmPlayer existia, pero solo la usaba la vista previa de tonos del editor, y fijada a 1f, o sea tambien al 100 %. En el historial de este proyecto nunca hubo un campo de volumen en el modelo; el porcentaje que el usuario recordaba, si acaso, venia del app anterior. Y un comentario en ExerciseEditorScreen hablaba de 'el control de volumen y sonido del beep' cuando solo habia selector de sonido: de los que en un escaneo rapido hacen concluir cosas que no son; se corrigio y ahora remite al ajuste global. DECISION DEL USUARIO entre un nivel fijo mas bajo o un ajuste: el ajuste, para afinarlo el mismo en el telefono en vez de recompilar. LA SOLUCION: MasterConfig.beepVolume en %, default 100 -como sonaban-, guardado en SettingsStore, y en Ajustes -> Player un SegmentedRow con 100/85/70/55/40 % sobre la curva que ya existia (85 son unos -3 dB, 70 unos -5). Es uno solo para todos los pitidos, no por etapa. Al tocar un nivel suena un pitido a ese nivel, que es la unica forma de afinarlo contra la musica sin arrancar un training; se le pasa el nivel elegido en vez de leerlo de Ajustes porque se llama en el mismo toque que lo guarda. El servicio lee el nivel en CADA pitido y no al arrancar, para que un cambio con una corrida en marcha se note en el siguiente. La vista previa del editor tambien usa el nivel de Ajustes, para que no suene distinto que en el training. Los pitidos siguen sin pedir foco de audio: se oyen encima de la musica sin bajarla, que es lo que el usuario quiere.
- [ ] **TD-044** Feedback haptico en el player (skip, check, pause)
  - Vibracion corta al hacer skip, check o pause en el player. Confirmacion tactil sin necesidad de mirar la pantalla. Usar VibrationEffect.createOneShot con duracion corta (~50ms) para no ser intrusivo. Solo en acciones del usuario, no en transiciones automaticas.
- [ ] **TD-043** Preview del siguiente ejercicio en REST
  - Durante los steps de REST, mostrar el glyph/icon del proximo ejercicio mas grande y prominente, no solo un label de texto. Ayuda a prepararse mentalmente para el siguiente ejercicio.
- [ ] **TD-042** Resumen de stats en Historial
  - Al abrir History, mostrar un header compacto con: total de sesiones, tiempo total acumulado, ejercicio mas frecuente. Datos derivados de vm.sessions.
- [ ] **TD-041** Ring de progreso en el player
  - Ademas de la barra lineal de progreso, un ring circular alrededor del clock que muestra cuantos del step actual ha transcurrido. Mas visual y moderno. Calcular fraccion con playerRemainingMs y la duracion total del step.
- [ ] **TD-014** Keep-screen-on durante la corrida (opcional)
  - El player a pantalla completa podria beneficiarse de keep-screen-on
- [ ] **TD-013** Accesibilidad (opcional)
  - contentDescription en iconos del player y areas tactiles >=48dp
- [ ] **TD-012** Onboarding / estado vacio (opcional)
  - Cuidar el primer arranque: seed de ejemplos vs vacio + tutorial breve
- [ ] **TD-011** Animaciones de ejercicio (opcional)
  - Placeholder para animacion por ejercicio; definir si entra en el roadmap
- [ ] **TD-010** Catalogo de ejercicios expandido (opcional)
  - Revisar/expandir ExerciseCatalog y ExerciseIcons pensando en el app independiente

### Fix

- [ ] **TD-129** Fix: la velocidad de la caminata no llegaba al registro
  - ENCONTRADO el 19-sep-2026 leyendo su sesion: el training tenia la caminata a 6 y 4 km/h y la sesion llego sin velocidad. En todo el respaldo, 'speedKmh' solo aparecia en los trainings.

LA CAUSA: los pasos viajan del app al servicio del player como JSON -por el Intent al arrancar y en las preferencias para sobrevivir a que el sistema mate el proceso-, y ese serializador vivia dentro de WorkoutPlayerService escrito CAMPO A CAMPO. Cuando la caminata gano velocidad (TD-124) no se agrego a la lista, el servicio recibio la caminata sin ella y registro nada. Faltaban ademas workoutBaseName, variantName, rotating y secPerRep, de antes.

EL ARREGLO: PlayerStepJson, puro y en el modelo, con un test que arma un paso con TODOS los campos distintos de su valor por defecto -y verifica por reflexion que ninguno se quedo en el defecto- y exige que vuelva identico. El proximo campo que se agregue a PlayerStep y no se agregue al serializador rompe el test en vez de perderse en silencio. Lo probo de inmediato: al agregar weightType, barWeight y dumbbellCount (TD-130) el test obligo a llevarlos.

LA LECCION, que es la de toda la semana: una lista escrita a mano se queda atras en silencio cuando lo de al lado crece.
- [ ] **TD-128** Fix: un training recien sembrado no se podia asignar hasta reiniciar el app
  - REPORTADO el 18-sep-2026, al intentar repartir el primer dia de NIKO: 'la rutina NIKO - Glute Day no me da opcion para asignar'.

LA CAUSA: 'Asignar a...' solo aparece si el training tiene uid -sin uid no hay con que emparejarlo en el otro telefono-, y el uid se rellenaba AL ESCRIBIR EN DISCO, dentro de saveTrainings, sin devolverlo. Lo guardado y lo que la pantalla tenia en la mano dejaban de coincidir: el training sembrado vivia sin uid en memoria hasta el siguiente arranque del app, y hasta entonces no se podia repartir.

EL ARREGLO: saveTrainings devuelve la lista ya con los uid puestos y persist() se queda con lo devuelto. El sitio unico de escritura sigue siendo el mismo; lo que cambia es que ahora lo que se guardo y lo que se ve son lo mismo.

LA LECCION, que es la de siempre en este proyecto: una funcion que corrige los datos al guardarlos tiene que devolver lo corregido. Si no, hay dos versiones de la verdad y la de la pantalla es la vieja.
- [ ] **TD-120** Escribir en la sesion del 17-sep el feedback que el app boto
  - La sesion del 17-sep-2026 se grabo con la version que botaba lo marcado en la tarjeta del peso (TD-117). El usuario lo conto por chat serie por serie esa noche y pidio escribirlo: puente ligero/ligero/sin marcar, carry y sentadilla ligero en las tres.

Como TD-105: una correccion de un dato concreto, una sola vez, y la sesion queda EDITED. La logica es pura (MasterDefaults.withSep17Feedback) y tiene test, porque toca datos reales: solo escribe en ejercicios sin nada marcado y con el numero de series que coincide; si el usuario lo hubiera marcado distinto, lo suyo gana. Lo del 16-sep no se reconstruye porque no se conto serie por serie.
- [ ] **TD-119** Borrar una sesion del historial solo se desliza con la tarjeta cerrada
  - PROPUESTO por el usuario el 17-sep-2026, con captura: 'el boton de borrar, no crees que deberia aparecer solo cuando esta colapsado?'. Si.

LO QUE SE VEIA: con la sesion expandida, deslizar a la izquierda arrastraba la tarjeta entera -las series quedaban cortadas por el borde- y el tacho aparecia flotando a media altura de una tarjeta muy alta. Y la sesion expandida es justo la que se esta leyendo: un gesto lateral sin querer mientras se hace scroll le ponia 'borrar' delante.

EL CAMBIO: SwipeActionsRow gana un parametro 'enabled'; apagado, la fila no se desliza y si estaba abierta se cierra. En el historial se apaga mientras la sesion esta expandida. Se apaga en vez de quitar las acciones para no cambiar la forma del arbol de Compose, que rehace el contenido entero y pierde el estado que lleve dentro. Las demas listas que usan el mismo componente no cambian.
- [ ] **TD-117** Fix: lo que se marca en 'How did the weight feel?' nunca llega al historial
  - BUG, destapado el 17-sep-2026. El usuario marco en la tarjeta 'How did the weight feel?' casi todas las series de la sesion -puente 6 'muy ligero', 16 'ligero'; carry y sentadilla 'ligero' en las tres- y en el respaldo NO HAY NI UNA. En todo el historial no existe un solo feedbackDeltaKg. Lo que marca se pierde, siempre.

LA CAUSA, dos agujeros en SessionRecorder:
1. setFeedback solo escribe si el registro del ejercicio YA existe (records[key]?.let). El registro nace al completar la primera serie, y la tarjeta se toca DURANTE la serie: lo marcado en la primera serie se descarta en silencio.
2. putSet reconstruye el registro desde cero en cada serie completada, sin copiar el feedback que ya tenia. Lo marcado en la serie 2 se guarda y al terminar esa serie se borra.
Entre los dos no hay momento en que un toque sobreviva.

Y UN LIMITE DE DISENO: el feedback es UNO por ejercicio (el ultimo toque gana), pero la tarjeta sale en cada serie y el la usa por serie. En una piramide 6/16/31 'el 6 muy ligero' y 'el 31 bien' son datos distintos, y el modelo solo guardaria uno. Ademas el chip marcado en la serie 2 enseña lo que se eligio en la 1.

LO QUE SE PERDIO: todo lo marcado hasta hoy. De las sesiones del 16 y 17-sep solo queda lo que el conto por chat, anotado en docs/coach-log.md.

EL ARREGLO (v1.0.258). El feedback se guarda POR SERIE en SetRecord.feedbackDeltaKg, y en SessionRecorder vive en un mapa propio, aparte de los registros: espera a que su serie se complete y se le pega al armar el registro, sin importar el orden. El comando FEEDBACK lleva la serie, la tarjeta marca el chip de ESA serie -antes enseñaba el de la anterior-, y los dos historiales dicen al lado de cada serie lo que se marco, con las mismas palabras que los botones. El feedback por ejercicio queda como resumen (el de la ultima serie marcada) y para leer registros viejos. No marcar sigue siendo null: no es un 'justo'.

LOS TESTS: los dos que habia probaban el orden en que NO se usa -marcar despues de completar- y uno afirmaba como correcto el agujero ('setFeedback on non-existing record is no-op'). Se sustituyeron, con la razon escrita, por ocho que reproducen el uso real, mas dos del JSON.
- [ ] **TD-115** El boton central no desaparece en los ejercicios por reps: se apaga
  - REPORTADO el 16-sep-2026, y tambien lo llevaba aguantando dias: 'el boton central de los controles, que desaparece en los ejercicios con reps, supuestamente le pusimos un circulo para no dejarlo vacio pero es tan imperceptible que igual ni se nota'.

DE DONDE VENIA: el hueco del centro se reserva siempre para que los controles no cambien de sitio al pasar de un ejercicio por tiempo a uno por repeticiones, y en los manuales se rellenaba con un circulo de SOLO BORDE al 12% de blanco. Sobre un video, eso no se ve.

EL CAMBIO: el boton se queda, deshabilitado. Mismo cristal translucido, mismo icono de pausa al 30% y sin responder al toque. Es como lo resuelve YouTube y fue el quien lo trajo: 'pense que como son botones translucidos era imposible representar un boton deshabilitado y translucido PERO veo que YouTube lo hace'. GlassButton gana un parametro 'enabled', asi que cualquier otro control puede apagarse igual en vez de desaparecer.

POR QUE UN BOTON APAGADO Y NO UN HUECO: un hueco dice 'falta algo'. Un boton apagado dice que ahi no hay nada que pausar porque un ejercicio por repeticiones no corre contra el reloj, que es la verdad.
- [ ] **TD-114** Fix: el indicador de refrescar salta al desplazarse y se queda girando
  - REPORTADO el 16-sep-2026, y lo llevaba aguantando dias sin decirlo porque habia otras prioridades: 'justo arriba de LUMBAR (bad day) hay un circulo de progreso que se queda ahi girando y girando, se activa al yo apenas desplazarme hacia arriba en la lista, es como si estuviera muy sensible... la idea era que eso salga cuando hiciera un swipe down deliberado, pero no al querer ver el primer registro de la lista'.

DOS CAUSAS, no una.

1. EL SOBRANTE DE UN IMPULSO CONTABA COMO TIRON. onPostScroll aceptaba deltas vinieran del dedo o de un fling. Al subir la lista de un manotazo, esta llegaba al tope con carrera y el sobrante inflaba el tiron SOLO. Y como onPreFling -el unico sitio donde el tiron se soltaba- ya habia pasado antes de ese fling, nadie lo bajaba: el indicador se quedaba puesto. De ahi el 'girando y girando' sin que el hubiera tirado de nada. Arreglado filtrando por NestedScrollSource.Drag, y con un onPostFling de red de seguridad para que nada pueda quedarse desplegado despues de un impulso.

2. NO HABIA ZONA MUERTA. Estando arriba del todo, el primer pixel de arrastre hacia abajo ya pintaba indicador, y mirar el primer training de la lista se pasa del tope sin querer. Ahora los primeros 32dp de arrastre no cuentan.

Y DE PASO, LA RUEDA. Era un CircularProgressIndicator siempre, asi que giraba tambien mientras se tiraba: le decia al usuario 'estoy sincronizando' cuando no habia empezado nada. Ahora mientras se tira hay una flecha que se endereza hasta apuntar arriba al llegar al umbral, y la rueda gira SOLO mientras se sincroniza de verdad.

NOTA PARA LA PROXIMA: lo aguanto varios dias sin reportarlo. Cuando algo del app le moleste, mejor saberlo aunque no sea prioridad; anotarlo cuesta menos que aguantarlo.
- [ ] **TD-113** Fix: la pregunta del dolor se dibuja encima de la ultima tarjeta del training
  - BUG, visto en captura del telefono el 16-sep-2026: en la pantalla previa del training, 'How is your back right now?' se dibujaba ENCIMA de la ultima tarjeta del training. En LUMBAR se leia el texto de la pregunta atravesando el '1 Exercise - 5:00' de Cool Walk.

LA CAUSA: el bloque de abajo -pregunta + boton de empezar- flota sobre la lista, y la lista reservaba para el un hueco ESCRITO A MANO, 96.dp, medido cuando ahi solo habia un boton. La escala de dolor (TD-089) anadio etiqueta, descriptor y dos filas de numeros, y el hueco se quedo a menos de la mitad de lo que hacia falta.

EL ARREGLO: el alto del bloque se MIDE con onSizeChanged y alimenta el contentPadding de la lista, asi que el hueco se ajusta solo a lo que ese bloque ocupe -con la pregunta o sin ella, en un idioma o en otro-. Ademas el bloque pasa a ser opaco: aunque la lista termine donde el empieza, al hacer scroll las tarjetas pasan por detras y sin fondo se leerian encima de la pregunta.

LA LECCION, que ya se habia pagado con TD-106: cada vez que algo crece en una pantalla, lo que le reservaba sitio con un numero fijo se queda corto en silencio. Medir en vez de escribir el numero.

Y DE PASO, viendo la captura del arreglo: 'creo que tenemos espacio perdido, mira debajo de 0 - No pain'. El descriptor reservaba DOS lineas escritas a mano para que al cambiar de numero no empujara los botones, y ninguno de los once llega a dos lineas: debajo quedaba siempre una linea vacia. Ahora ese alto se mide -el del descriptor mas largo a ese ancho-, asi que la caja ocupa lo que de verdad hace falta y sigue sin empujar nada. Mismo error que el hueco de 96.dp, dos veces en la misma pantalla. Ademas el hueco entre las filas de numeros pasa a ir ENTRE ellas: el de despues de la ultima se sumaba al que ya pone quien coloca la escala.

Y UNA TERCERA, que el vio venir -'y vamos por una tercera'-: al medir el alto del descriptor se uso un TextStyle construido a mano (12.sp, SemiBold) en vez del estilo con el que Text pinta de verdad, que es el del tema fusionado con esos valores. Otra familia y otro interlineado: el alto salio corto y el texto se dibujo recortado, sin la cola de la 'y' de 'Hardly'. Arreglado midiendo LINEAS en vez de pixeles y dejando que el propio Text reserve el alto con minLines; equivocarse asi cuesta una linea de mas, nunca una letra partida.

LAS TRES SON EL MISMO ERROR: un numero puesto a mano para reservarle sitio a otra cosa -96.dp, dos lineas, un alto en pixeles-. Cada vez que lo de al lado crece, ese numero se queda corto EN SILENCIO y solo se ve en una captura. La regla que queda: el sitio lo reserva el componente con lo que ya sabe (onSizeChanged, minLines), y si hay que medir texto se mide con LocalTextStyle.current.merge(...), el mismo estilo con el que se pinta.
- [ ] **TD-100** Dos instancias del mismo ejercicio en un workout se funden en un registro
  - ENCONTRADO al arreglar TD-099 y levantado a peticion del usuario. SessionRecorder agrupa por ExerciseKey (exerciseId, workoutIndex), asi que si un workout repite el mismo ejercicio del catalogo, las dos apariciones escriben en la MISMA casilla: la segunda pisa las series de la primera y en el historial queda un solo registro, con el nombre y las series de la ultima. Se pierde la mitad del trabajo hecho. DONDE MUERDE HOY: es la razon por la que la plancha lateral de la rutina lumbar necesito dos entradas de catalogo, ex_side_plank_l y ex_side_plank_r (TD-086), en vez de una con nota 'cada lado' como hace el resto del catalogo. Se eligio asi a proposito para no perder las series de un lado, pero es rodear el fallo, no arreglarlo. EL FIX APARENTE: meter exerciseIndex en la clave, que desde TD-099 ya viaja en el registro. Dos apariciones del mismo ejercicio pasan a ser dos filas. LO QUE HAY QUE PENSAR ANTES, porque cambia como se cuenta el historial: - Con la clave nueva, un workout con 'Pushups' dos veces pasa de una fila a dos. Es mas fiel, pero es un cambio visible en trainings que el usuario ya tiene (MASTER repite ejercicios en varios sitios: comprobarlo antes). - ExerciseHistoryScreen agrupa por ejercicio a lo largo del tiempo; hay que ver si dos filas por sesion le estropean la serie o la mejoran. - Las sesiones ya guardadas no se pueden separar hacia atras: lo que se fundio, se fundio. - Si se arregla, la plancha lateral podria volver a ser UNA entrada de catalogo con nota, y el catalogo quedaria mas limpio. Eso seria un cambio aparte y posterior. Relacionado con TD-101, que es el que decide que se puede tocar del historial y que no.
- [ ] **TD-092** Fix: la nota del ejercicio se dibuja una linea encima de otra
  - REPORTADO POR EL USUARIO con captura del dispositivo el 13-sep-2026: 'el texto de la nota se esta apilando'. En la etapa PREPARE de Hip Hinge, la nota 'STICK ON NAPE, MID-BACK AND SACRUM' sale en tres lineas pintadas unas encima de otras, ilegible. CAUSA: el Text de la nota (PlayerScreen) pone fontSize 40sp pero no lineHeight, y la tipografia por defecto de Material -AppTypography = Typography() sin personalizar- trae un lineHeight ABSOLUTO de 20sp en bodyMedium. Con glifos de 40sp en cajas de linea de 20sp, la segunda linea se dibuja sobre la primera. No es un fallo nuevo: lleva ahi desde siempre y nunca se destapo porque las notas eran de dos o tres palabras ('each side', 'alternating') y nunca pasaban de una linea. Las notas del training lumbar (TD-086) son frases enteras y lo sacaron a la luz. EL TITULO YA LO RESOLVIA por su cuenta: titleStyle() escribe lineHeight = size * TITLE_LINE_RATIO, y el comentario de ExerciseTitle cuenta que medir el alto real de dos lineas costo una pasada en falso. A la nota le faltaba lo mismo. EL FIX: lineHeight explicito en la nota y en el contador de series, los dos con el mismo ratio del titulo, mas LineBreak.Heading en la nota para que reparta en lineas parejas en vez de dejar una palabra suelta al final. El contador de series va igual aunque hoy sea de una sola linea: comparte sitio y tamano con la nota, y su caja de linea tambien estaba a la mitad de sus glifos. NO MUEVE NADA MAS: la nota vive encima del hueco elastico, asi que crecer de una a tres lineas se lo come ese hueco y ni el reloj ni los controles cambian de sitio (lineamiento de MASTER). PENDIENTE APARTE: las notas del lumbar son largas para un slot pensado para dos palabras. Con el interlineado arreglado caben y se leen, pero si el usuario las quiere mas pequenas, eso es una decision de diseno aparte.
- [ ] **TD-064** Fix: quitar rotativo a un workout borra todas las variantes menos la primera
  - makeWorkoutSimple conserva los ejercicios de la PRIMERA variante y descarta el resto: w.copy(rotating = false, exercises = w.variants.firstOrNull()?.exercises, variants = emptyList()). Un workout rotativo con 3 variantes pierde dos sin aviso y sin deshacer. EL COMPORTAMIENTO DESEADO, en palabras del usuario: 'rotativo es que los workouts rotan, si le quito el rotativo deberia simplemente no rotar, no borrar nada'. O sea que el flag gobierna COMO se recorren las variantes, no DONDE viven los ejercicios; quitarlo no puede ser una operacion destructiva. Se descarto anadir un dialogo de confirmacion: confirmar una perdida de datos no deseada no arregla que la perdida no deba ocurrir. El bug lleva ahi desde que existe la funcion y no se habia notado. NO ES SOLO ESTE FIX: al revisarlo el usuario pregunto 'cual es la interfaz para hacer de un workout una variante, no la ubico', y esa pregunta abre el modelo entero de workout/variante, que hoy tiene dos representaciones distintas para lo mismo (exercises sueltos cuando es simple, exercises dentro de variants cuando es rotativo) y es de donde nace la perdida de datos al convertir. Revisar el modelo y los flujos de conversion en su propio espacio antes de tocar codigo. CASO DE REFERENCIA, senalado por el usuario: el rotativo 'Strength' del training MASTER funciona como se espera y es el que hay que mirar al abordarlo. Precision de vocabulario: el usuario lo describe como 'dentro de Strength hay 2 workouts, uno lower y otro upper, cada uno con sus ejercicios independientes', pero en el modelo Strength es UN workout con rotating=true y dos VARIANTES, Lower (12 ejercicios) y Upper (5). Lo que el usuario llama workout ahi es lo que el codigo llama variante, y esa distancia entre el vocabulario del usuario y el del modelo es parte de lo que hay que resolver. Comprobado en sus datos del 30-ago-2026: MASTER tiene tambien 'Cardio' rotativo con 4 variantes (Rope Jumping, Tire Jumping, Shadow Boxing, Running), donde quitar el rotativo hoy borraria tres. Con Strength borraria los 5 ejercicios de Upper.

### Mantenimiento

- [ ] **TD-135** El codigo y la documentacion dicen lo que dice la pantalla
  - PEDIDO el 19-sep-2026, despues de una ruta que no existia: se le dijo 'Ajustes → Manage profiles → toca Niko', y en su pantalla eso es 'Settings → Coach → People'. El nombre salio del codigo -manageProfiles, ProfilesScreen- y no de lo que el ve. Sus palabras: 'actualiza el codigo para que refleje lo que hay en pantalla, y altamente probable que la documentacion tambien este desactualizada'. Lo estaba.

EN EL CODIGO: las claves de Strings pasan a people/peopleDesc; ProfilesScreen.kt pasa a PeopleScreen.kt; showProfiles y onManageProfiles, a showPeople y onPeople. La descripcion en pantalla dice ahora lo que hace: 'Who can receive trainings, and what each one has assigned'. El modelo Profile y los metodos del repositorio se quedan: hablan del servidor, donde la tabla se llama profiles, y el comentario de PeopleScreen deja escrita la equivalencia.

EN LA DOCUMENTACION: AGENTS.md decia 'Ajustes → Import data' (es Settings → Data → Import backup); decia que un training sembrado llega a cualquier instalacion, cuando desde TD-108 las rutinas del coach miran el perfil; no explicaba como le llega una rutina a otro atleta ni que una revision exige reasignar; y no mencionaba las instrucciones del catalogo. coach.md no tenia la rutina de NIKO, el equipo, los objetivos ni sus documentos. Todo corregido, y AGENTS.md gana una tabla de rutas con las palabras de la pantalla y la regla: antes de dar una ruta, mirar Strings.kt.

Al escribir esa tabla casi se repite el error con el boton de instrucciones del player; se verifico en el codigo antes de dejarlo: es un icono de lista en la franja de arriba, que sale al tocar la pantalla.
- [ ] **TD-093** Permisos por prefijo, para que los scripts dejen de pedir permiso
  - EL PROBLEMA, visto el 13-sep-2026: las tres reglas de .claude/settings.local.json son de coincidencia EXACTA, y una de ellas incluye el -Message completo de un build de hace meses ('release: TD-048 Live Update notification + TD-049 color naranja en Now Bar'). Como el mensaje cambia en cada build, esa regla no vuelve a coincidir nunca y todo termina evaluado caso por caso. Ese dia build-debug.ps1 se rechazo una vez -etiquetado como 'Git Destructive' pese a que el script no toca git- y paso sin problema al reintentarlo, y un adb push al almacenamiento compartido se rechazo y no se pudo completar. LO QUE HAY QUE HACER: reglas por PREFIJO para los scripts del repo (build-debug.ps1, build-release.ps1, verify-compile.ps1, run-tests.ps1, forge-status.ps1) con cualquier argumento, y para los adb de solo lectura que se usan a diario (devices, shell ls, pull, exec-out screencap). Y limpiar las dos entradas exactas que ya no le sirven a nadie. LO QUE NO: nada que borre datos del dispositivo. adb uninstall no entra en ninguna lista, por lo mismo de TD-052 y TD-054. POR QUE IMPORTA ahora mas que antes: el ciclo de coach (docs/coach.md) termina siempre en 'siembro y hago build', asi que cada rutina nueva pasa por ahi. VALIDADO EL 15-sep-2026, a peticion del usuario: 'llevas varios dias sin tropezar con eso... tal vez solo lo estas repitiendo porque lo tienes anotado y no porque realmente sea obstaculo'. Tenia razon. LOS NUMEROS: en tres dias se corrieron 16 build-debug (v1.0.234 a la 250), 2 releases con gh y git push, y una decena de comandos adb -pull, screencap, install, shell ls-. **Rechazos: dos, los dos del primer dia**: build-debug una vez, etiquetado 'Git Destructive' pese a que el script no toca git, y que paso al reintentarlo; y un adb push a almacenamiento compartido, que ya no se volvio a necesitar porque la siembra por codigo lo dejo sin uso. CONCLUSION: dejo de ser un obstaculo real. No se descarta -si vuelve a estorbar, aqui esta el analisis hecho- pero no vale el tiempo hoy. Queda como pendiente de baja prioridad y sin reproponerse hasta que haya un tropiezo nuevo que lo justifique.
- [ ] **TD-071** Llegar al video e instrucciones de un training asignado sin duplicarlo
  - A la ficha de video e instrucciones (ExerciseMediaCard) no se llega desde un training asignado. Vive solo dentro de ExerciseEditorScreen, y a ese se entra por Edit -> workout -> ejercicio; un training asignado no ofrece Edit, solo Duplicate. El usuario ya tiene salida -duplicar el training y editar la copia- y le parece bien la regla, asi que esto no bloquea a nadie. Pero queda anotado porque es dano colateral: esa regla existe para proteger la ESTRUCTURA del training, que la sincronizacion si pisa, y el video y las instrucciones no corren ese riesgo porque viven aparte, por exerciseId del catalogo, y la sincronizacion no los toca nunca. Si algun dia molesta, el sitio natural es la vista previa: tocar un training asignado ya abre PreviewView con sus workouts y ejercicios, y desde ahi se podria entrar al material de cada uno sin reabrir la edicion. Salio al revisar TD-070.
- [ ] **TD-066** Opcional: script para publicar perfiles y asignaciones desde la PC
  - TD-063 dejo el app recibiendo asignaciones, pero los archivos users.json y users/<id>.json se generaron A MANO desde un backup del dispositivo. Asi no es usable: cada cambio de asignacion exige que alguien edite JSON. Falta un publish-profiles.ps1 que lea los trainings de una fuente -el export del usuario, o docs/ si se decide tenerlos versionados-, cruce una tabla de asignaciones tipo docs/assignments.json ({ 'niko': ['<uid>', '<uid>'] }) y genere los archivos listos para publicar, con la misma mecanica que build-release.ps1. Ojo con dos cosas al escribirlo: el uid de cada training publicado tiene que ser ESTABLE entre publicaciones, porque es la clave con la que el dispositivo empareja y conserva el id local que enlaza el historial; y publicar una lista vacia para alguien le retira sus trainings asignados, asi que conviene que el script avise de cuantos quita antes de escribir. BAJA A OPCIONAL con TD-067: asignar pasa a hacerse desde el telefono contra Supabase, asi que este script deja de ser el camino y queda como herramienta alterna para cuando estes en la PC, y solo si despues de TD-067 sigue haciendo falta.
- [ ] **TD-033** Arquitectura: Repository interfaces + MVI + Navigation + Testing
  - Fases 2-5 del plan en docs/plan-arquitectura.md. (2) Repository interfaces: TrainingRepository, SessionRepository, SettingsRepository como interfaces, WorkoutStore y SettingsStore las implementan, ViewModels reciben interfaces por constructor. (3) MVI: MasterState/MasterAction/MasterEvent, StateFlow + Channel, onAction() en vez de metodos sueltos, composables reciben state + onAction. (4) Compose Navigation type-safe con SavedStateHandle, migrar flags de navegacion del ViewModel a rutas. (5) Testing con Turbine + fakes: FakeTrainingRepository, FakeSessionRepository, FakeSettingsRepository, tests del ViewModel. Cada fase deja la app funcional y se ejecuta una a la vez.

## Hechos

### Branding

- [x] **TD-008** Rebrand del texto del wordmark (opcional)

### Bug

- [x] **TD-154** Apagar el video es una preferencia del telefono, no del training
- [x] **TD-153** El lado del ejercicio se corta en el player: DERECHA sale HA
- [x] **TD-075** Fix: el video del ejercicio pausa la musica (Spotify) al reproducirse
- [x] **TD-015** Fix drag-reorder en lista de trainings

### Feature

- [x] **TD-152** Los ejercicios con el peso del cuerpo tampoco dicen si costaron
- [x] **TD-147** El ejercicio unilateral: el lado entra en el modelo
- [x] **TD-145** El video es un campo mas del ejercicio: una sola tarjeta y sin carteles
- [x] **TD-139** Las instrucciones dejan de viajar en el APK: una tabla por exerciseId
- [x] **TD-140** Publicar un video desde el telefono: bucket en Supabase y boton en la ficha del ejercicio
- [x] **TD-141** Migrar los 7 videos publicados y retirar videos.json
- [x] **TD-138** Archivar trainings: la lista ensena lo que toca, no todo lo que existe
- [x] **TD-132** Una revision de una rutina ya asignada no le llega al atleta hasta reasignarla
- [x] **TD-110** El historial ensena el dolor y la nota de cada sesion
- [x] **TD-105** Revision 2 de la rutina, y corregir los pesos mal registrados del 15-sep
- [x] **TD-104** Inventariar el equipo disponible para poder disenar con lo que hay
- [x] **TD-098** Cargar el bloque de cadera y gluteo, que se le queda corto
- [x] **TD-090** Anotar en el historial la sesion del 13-sep que se hizo sin el app
- [x] **TD-088** Variante de dia malo: la movilidad antes de la caminata
- [x] **TD-086** El training LUMBAR se siembra desde el codigo, no se importa a mano
- [x] **TD-085** Series con trabajo y descanso propios: la piramide descendente en un solo ejercicio
- [x] **TD-080** Que ve un ejercicio que todavia no tiene video
- [x] **TD-079** El chrome del player se desvanece solo y deja el video limpio
- [x] **TD-078** El video del player se queda con la pantalla (reloj y controles superpuestos)
- [x] **TD-073** Editar el training mientras se esta ejecutando
- [x] **TD-070** Ver u ocultar el video es cosa de cada ejercicio en su training
- [x] **TD-068** Entrega automatica de asignaciones con aviso, y deslizar para refrescar
- [x] **TD-067** Asignar trainings desde el telefono
- [x] **TD-063** Motor de recepcion de trainings asignados
- [x] **TD-062** Repositorio remoto de videos con descarga bajo demanda y cache
- [x] **TD-061** Refinar la presentacion del video en el player
- [x] **TD-059** Instrucciones paso a paso por ejercicio
- [x] **TD-058** Video instructivo por ejercicio
- [x] **TD-057** Swipe-to-reveal en WorkoutRow y VariantRow + quitar el chevron inutil
- [x] **TD-053** Snapshot automatico de datos a almacenamiento compartido
- [x] **TD-051** Add from existing: reutilizar un workout de otro training
- [x] **TD-048** Icono de la barra de estado solo en segundo plano (estilo YouTube)
- [x] **TD-040** La tarjeta dice cuantos ejercicios y cuanto dura
- [x] **TD-039** Swipe-to-reveal en TrainingCards, en reemplazo del menu de 3 puntos
- [x] **TD-037** Tap en dia del calendario abre sheet con sesiones de ese dia
- [x] **TD-035** Atenuar pantalla del player cuando el timer esta en pausa
- [x] **TD-028** Skip por ejercicio en el player
- [x] **TD-021** Historial por ejercicio + sesiones parciales
- [x] **TD-009** Export/import de datos a JSON (PRIORITARIO)

### Fix

- [x] **TD-146** Apagar el video desde el player, y que lo editado en caliente se vea ya
- [x] **TD-142** Fix: republicar desde el arranque leia isCoach antes de que existiera
- [x] **TD-134** Fix: borrar un training dejaba su asignacion viva, y no habia donde quitarla
- [x] **TD-121** Fix: borrar una sesion, y las correcciones al arrancar, no escriben respaldo
- [x] **TD-108** La rutina lumbar solo se siembra en el telefono de su dueno
- [x] **TD-106** Fix: la tarjeta del peso se quedo sin sus botones al crecer la nota
- [x] **TD-102** Reordenar las dos sesiones lumbares que quedaron en alfabetico
- [x] **TD-099** Fix: el historial ordena los ejercicios por nombre y no por la rutina
- [x] **TD-082** Fix: la tarjeta de History crece y el badge se parte con nombres largos
- [x] **TD-065** Fix: build-release.ps1 borraria el release de videos al publicar
- [x] **TD-060** Respaldo: snapshot antes de importar y versionado por marca de tiempo
- [x] **TD-052** build-debug.ps1 no debe desinstalar automaticamente
- [x] **TD-050** Fix preventivo: la copia de un workout no clona sus variantes
- [x] **TD-049** Probar color naranja en el Now Bar
- [x] **TD-046** DaySheet: mostrar training expandido a nivel workout al abrir
- [x] **TD-045** Aumentar atenuacion de pausa en el player (0.35 -> 0.55)
- [x] **TD-038** Unificar indicadores del calendario a 8dp borde 1dp
- [x] **TD-036** Boton play transparente en TrainingCard
- [x] **TD-034** Confirmacion de delete con nombre en TrainingCard y SessionRow
- [x] **TD-031** Mover Clear history al TopAppBar de History
- [x] **TD-027** Historial agrupado por workout con badge parcial/total
- [x] **TD-026** Fix: WeekCalendar no se actualiza en vivo tras terminar training
- [x] **TD-025** Fix: circulo indicador de WeekCalendar muy pequeno
- [x] **TD-024** Ripple solo en chevron de WorkoutGroupCard
- [x] **TD-023** Fix: tap en card abre editor en vez de preview

### Forge

- [x] **TD-004** CI local integrado en scripts de build
- [x] **TD-003** Tests de dominio (Workout, PlayerStep) - verificado por tests
- [x] **TD-002** Tests de rotacion idempotente - verificado por tests
- [x] **TD-001** Tests del motor de pasos (StepEngine) - verificado por tests

### Mantenimiento

- [x] **TD-103** La rutina va por revision, no por una migracion en cada ajuste
- [x] **TD-087** Borrar los cinco ejercicios propios que quedaron sin usar
- [x] **TD-083** Auditoria de coherencia UI: un solo mecanismo para todas las listas
- [x] **TD-072** Ajustes: ensenar y liberar el espacio de los videos descargados
- [x] **TD-069** Pendiente: comprobar en dispositivo los avisos y la entrega automatica
- [x] **TD-056** Borrar handoff.md (TD-048 resuelto)
- [x] **TD-055** build-debug.ps1 deja de copiar el APK a Download del telefono
- [x] **TD-054** Regla: verificar firma antes de instalar y nunca desinstalar sin autorizacion
- [x] **TD-047** Documentacion: regla post-build en AGENTS.md + actualizar master-forge.md
- [x] **TD-030** Arquitectura: Koin DI
- [x] **TD-029** Regla: TD es done solo cuando el usuario aprueba tras probar en dispositivo
- [x] **TD-022** AGENTS.md como gatekeeper del harness
- [x] **TD-020** Eliminar referencias a entorno corporativo y mini-timer
- [x] **TD-007** Decidir sobre branding/icons.html sin trackear
- [x] **TD-006** Limpieza de codigo muerto

### Rebrand

- [x] **TD-032** Rebrand residual: eliminar todas las referencias a Athlete
- [x] **TD-019** Actualizar documentacion del rebrand
- [x] **TD-018** Renombrar repo de GitHub y actualizar URLs
- [x] **TD-017** Crear proyecto nuevo com.maurozegarra.master y migrar codigo

### Testing

- [x] **TD-016** Verificar self-update en dispositivo
- [x] **TD-005** Verificacion funcional en dispositivo

### UI

- [x] **TD-150** Los trainings archivados tambien se ordenan arrastrando
- [x] **TD-109** La escala de dolor describe cada numero, no solo los extremos
- [x] **TD-107** El centro del player cede sitio a la nota: contador junto a las reps y tarjeta de peso translucida
- [x] **TD-074** Nombre del ejercicio en el player: dos lineas como mucho, sin cortar palabras y con alto fijo
