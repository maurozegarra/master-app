# To-Do - MASTER

> Generado automaticamente por `forge-status.ps1` desde `docs/forge-todo.json`.
> No editar directamente; actualizar el JSON y regenerar con `.\forge-status.ps1`.
> Convencion de commits: `feat: TD-XXX ...` / `fix: TD-XXX ...`.

Progreso: **139 / 166** hechos, 27 pendientes.

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

### Deseable

- [ ] **TD-166** Circuitos en el editor de workouts
  - DESEABLE, no prioridad, decidido por el usuario el 25-sep: "cada vez lo veo mas improbable que yo entre y cree un Training". Los trainings entran desde el codigo (el coach los arma), y Workout.circuit (TD-137) ya funciona asi. Seria un interruptor "Circuit" en el editor del workout, junto al de rotativo.
- [ ] **TD-133** El app en español, para NIKO y para cualquiera que no lea ingles
  - PEDIDO el 19-sep-2026: 'niko no maneja el ingles, lo ideal seria que el app tenga soporte para español, apuntalo como un TD'. Para salir del paso ese dia se pusieron en español las instrucciones del catalogo (TD-131) y el texto libre de su rutina (TD-127 revision 3).

LO QUE FALTA: el app es English-only por decision de producto (I18n.get() devuelve EN y lang() es 'en'), pero la base existe: Strings es una clase con todas las cadenas y el catalogo ya tiene los nombres en los dos idiomas. Hace falta un Strings ES completo, un ajuste de idioma por telefono (no por training: el idioma es de quien lo usa), y decidir que pasa con los textos libres que escribe el coach -notas, nombres de bloques-, que no se traducen solos.

OJO: el idioma es del TELEFONO. Si el coach escribe en ingles para si y en español para NIKO, las notas de sus rutinas tienen que ir en el idioma de quien las recibe.

DESEABLE, no prioridad, decidido por el usuario el 23-sep: "la he visto bastante comoda a Niko, con las instrucciones en espanol le va bastante bien". Cuando se haga, ojo: Strings esta al tope de 255 parametros de la JVM y hay que reorganizarla en bloques antes de tener dos idiomas.

### Feature

- [ ] **TD-164** Corregir el dolor de la manana desde la pantalla Morning
  - LO PIDIO el usuario el 25-sep: contesto 1 en la alarma, le parecio "demasiado optimista", "y como no hay forma de editarlo, lo hice cuando tuve la oportunidad" -en la sesion, horas despues-, "pero tuve que hacer el esfuerzo de no olvidarlo".

HECHO en codigo el mismo dia: en Morning, tocar el numero de hoy abre la escala en el sitio y corrige (MorningAlarm.edit), a cualquier hora y conservando la hora del despertar. La sesion de ese dia no se reescribe: la serie se lee de las mananas.
- [ ] **TD-165** Dolor habitual y dolor de crisis, separados: la sesion ya no pregunta por la crisis
  - LO EXPLICO el usuario el 25-sep, al preguntarle por que no contesto el dolor final ni la irradiacion: "entendia que eso tenia que ver con los dias que estuve en crisis, y eso es importante distinguirlo. El dolor al despertar es un dolor habitual, hasta diria normalizado, y es muy distinto al dolor de los dias que estuve en crisis. Si bien usamos la misma escala, estan separados por una razon. El dolor de la crisis ya se fue, lo que me queda es el dolor habitual y eso es lo que hay que ir mejorando. No marque porque ya no existe ese dolor ni al comienzo ni al final, y sin ese dolor, no hay irradiacion."

HECHO en codigo el mismo dia: la pantalla previa ya no pregunta "How is your back right now?", y al final, el dolor antes/despues y la irradiacion van plegados detras de "Back crisis today" -abierto solo si se toca, o si ya tiene algo-. El habitual lo pregunta la alarma y la sesion lo toma de ahi. Anotado en docs/coach.md: un Pain before vacio ya no es un olvido.
- [ ] **TD-159** Alarma: etiquetas de la noche, para cruzarlas con el dolor de la manana
  - PROPUESTO el 24-sep y registrado el 25 (salio de TD-158, donde solo quedaba mencionado).

POR QUE, y es la de mas valor de las cinco: el 19-sep su mejor dato vino de la NOCHE y no del ejercicio -colchon rotado y almohada bajo las rodillas: cinco horas sin dolor-, y la noche siguiente empeoro por dormir de lado cuando NIKO se paso a su cama. Nadie va a recordar eso a mano semanas despues.

QUE: tras contestar el dolor en la alarma, etiquetas OPCIONALES de un toque -almohada en las rodillas, dormi de lado, cena tarde, entrene tarde, otra cama, dormi poco-, guardadas en la manana del dia. En la pantalla Morning, con unas semanas de datos: el dolor medio con y sin cada etiqueta ("con almohada 1.2, sin ella 2.8").

A DECIDIR: la lista de etiquetas -la propone el coach, la ajusta el usuario-; si se contestan en la misma pantalla de la alarma o despues, en Morning, para no alargar el primer toque del dia.
- [ ] **TD-160** Alarma: aviso para ir a dormir, calculado desde la hora de la alarma
  - PROPUESTO el 24-sep, registrado el 25. Un aviso a la noche -por ejemplo 7 h 30 antes de la alarma del dia siguiente- y, con la hora en que se apaga, cuanto se durmio. Da contexto a la serie del dolor: una manana mala tras cinco horas de sueno no dice lo mismo que tras ocho.
- [ ] **TD-161** Alarma: sonido que sube de a poco
  - PROPUESTO el 24-sep, registrado el 25. Que el volumen empiece bajo y llegue al maximo en unos 30 s, en vez de arrancar al maximo. Es un despertador para alguien con dolor lumbar: un sobresalto al despertar no ayuda.
- [ ] **TD-162** Alarma: control rapido en el panel de Android
  - PROPUESTO el 24-sep, registrado el 25. Un Quick Settings tile para encender, apagar o saltar la alarma de manana sin abrir el app.
- [ ] **TD-163** Alarma: widget con la proxima alarma y las ultimas mananas
  - PROPUESTO el 24-sep, registrado el 25. En la pantalla de inicio: la hora de la proxima alarma y el dolor de las ultimas 7 mananas, con su color y su numero.
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

DECIDIDO el 22-sep con el usuario: la alarma, DENTRO de MASTER y no como app aparte, pero en su propio paquete (morning/) con solo tres puntos de contacto -la sesion toma el dolor del dia, el respaldo lo incluye, una entrada en Settings-, para poder sacarla si en una semana de prueba estorba al app de ejercicio. Sacarla seria mover el paquete y cambiar esos tres puntos por un puente entre apps.
(1) REEMPLAZA a su despertador. La primera semana conviene su alarma de siempre dos minutos despues, de respaldo.
(2) Una hora por dia: lunes, miercoles y jueves (presenciales) 5:00; los demas, 7:00. Cada dia se puede apagar.
(3) Posponer 5 minutos, y la pregunta del dolor igual al apagarla de verdad.
Apagarla ES contestar: la pantalla de la alarma es la escala 0-10, con un Dismiss pequeno para apagar sin contestar. Despues queda una notificacion fija "Tap when it eases" y los minutos los calcula el app. El dato es del DIA: los dias de descanso tambien cuentan.

HECHO en codigo el 22-sep (v1.0.316-320), funcionando en su telefono; queda PENDIENTE la semana de prueba, desde el miercoles 23, para decidir si se queda en MASTER. Paquete morning/: Morning.kt (horario, entrada del dia, puro y con tests), MorningStore (su propio archivo de preferencias), MorningAlarm (setAlarmClock, posponer, apagar, "aflojo"), MorningReceiver (la hora, "It eased", y reprogramar al reiniciar, actualizar o cambiar la hora), MorningRingService (sonido en volumen de alarma, vibracion, se pospone sola a los 10 min hasta 3 veces), MorningActivity (0-10 grande sobre el bloqueo) y MorningSettings. Los tres puntos de contacto estan marcados en el codigo: applyMorning en el ViewModel, el campo morning del respaldo (formato 4) y la tarjeta de Settings.

CUATRO FALLOS en la prueba, los cuatro del asistente:
(1) El app se caia al encenderla: setAlarmClock SI pide permiso de alarma exacta desde Android 12, y se dio por hecho que no. USE_EXACT_ALARM, y reschedule ya no se cae si falta.
(2) La pantalla no se encendia: canUseFullScreenIntent() decia que si y Samsung lo negaba igual, porque la operacion USE_FULL_SCREEN_INTENT estaba en su modo por defecto. Ahora se mira la operacion; se abrio por adb con permiso del usuario (appops set ... allow), y el app lleva al ajuste si vuelve a faltar.
(3) La pantalla tardaba 10 s: Android difiere la notificacion de un servicio en primer plano salvo FOREGROUND_SERVICE_IMMEDIATE.
(4) "Good morning" no se iba al contestar: cancelarla desde fuera no sirve mientras el servicio sigue en primer plano; ahora la quita el servicio en onDestroy.
Y el texto de abajo quedaba bajo la barra de navegacion.
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

HECHO el 25-sep, pedido por el usuario "para que manana tenga todo lo que necesita" (NIKO 6, sabado 26): Workout.circuit. El motor arma la cola normal y la reordena round a round -mismos pasos, otro orden-, con la preparacion de cada ejercicio solo la primera vez. La reubicacion tras editar en marcha compara por round y despues por ejercicio. NIKO 6 lo usa: saco 3 min -> sprawl 30 s -> 1 min, cuatro veces (NIKO_REVISION 14). Sin interruptor en el editor todavia: los circuitos entran desde el codigo. CircuitTest.

Y DE PASO, del 25-sep: NIKO hizo la cuerda entera, volvio atras solo para marcar, y el registro de 180 s se piso con uno de 1 s. SessionRecorder ahora conserva lo hecho: volver a una serie completada no la acorta ni la desmarca.
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

DECIDIDO el 23-sep con el usuario, que lo planteo: "me sigue pareciendo raro que diga 2 reps" y "me propusiste pasarlo a tiempo y no veo que sea el camino". Tiene razon: el tiempo cambia con el ritmo y no dice nada del agarre; lo que se entrena es peso y distancia.

HECHO en codigo el mismo dia, pendiente de validar: WorkMode.DISTANCE. Se comporta como REPS -paso manual, lleva peso y su tarjeta, se confirma al terminar- con otra unidad: el player dice "36 M", la notificacion "36 m", el editor tiene "Meters" y el historial "36 m x 20 kg". El registro guarda los metros en SetRecord.distanceM y deja reps en 0; lo guardado antes sigue diciendo "2 reps". El estimado usa ~1 s por metro. Los textos van en su bloque (DistanceStrings), por el tope de Strings.

EN LAS RUTINAS: el suitcase carry del completo y del corto pasa a 36 m POR LADOS (revision 15): el 22-sep anoto que el agarre izquierdo le cuesta mas, y con un registro por mano eso se ve serie a serie. El paseo del granjero de NIKO 3 pasa a 36 m sin lados (revision 11 de NIKO). DistanceTest.
- [ ] **TD-094** Publicar los videos de los nueve ejercicios del lumbar
  - LOS NUEVE MOVIMIENTOS que entraron al catalogo con TD-086 -ex_walk, ex_hip_hinge, ex_curl_up, ex_side_plank_l, ex_side_plank_r, ex_bird_dog, ex_glute_bridge, ex_suitcase_carry, ex_box_squat- no tienen video. El gato-camello si: usa ex_cat_cow, que ya estaba publicado desde TD-062. EL CAMINO, que es el unico automatizable y esta documentado en AGENTS.md ('Como poner contenido en el telefono'): renombrar cada mp4 a <exerciseId>.mp4, subirlo con 'gh release upload videos', agregar su entrada a videos.json con file, rev y bytes, y hacer push de videos.json a main. El app lee el manifiesto de GitHub raw y baja cada video la primera vez que hace falta. **No hay que publicar version nueva del app.** PENDIENTE DEL USUARIO: pasar los archivos. Los que ya estan publicados pesan entre 1.7 y 4.8 MB, que es la escala que conviene. DETALLE UTIL: si solo hay un clip de plancha lateral, las entradas de ex_side_plank_l y ex_side_plank_r pueden apuntar al mismo archivo; el app lo baja una vez por cada id. OJO: el push de videos.json necesita autorizacion explicita del usuario, como cualquier push.
- [ ] **TD-081** Unificar los tres iconos de la franja del player (instrucciones, editar y video)
  - NOTA DEL USUARIO, sin decidir todavia: 'no me convence del todo un boton para instrucciones y otro boton para editar el ejercicio, creo que deberiamos unificarlo o pensar en algo mas'. Hoy la franja de rutina lleva dos iconos sueltos a la derecha: InstructionsButton (lista, abre un ModalBottomSheet con los pasos numerados; no se dibuja si el ejercicio no tiene instrucciones) y EditExerciseButton (lapiz, llama a editRunningExercise; no se dibuja si el training viene asignado). O sea que segun el ejercicio y el training puede haber dos iconos, uno o ninguno, y la franja cambia de contenido sin que el usuario sepa por que. A PENSAR: un solo punto de entrada -menu, sheet unico con pestanas, o accion contextual- en vez de dos iconos que compiten por la misma esquina. Contexto: surge tras descartar las instrucciones como relleno del hueco del video (TD-080), que las devuelve a vivir detras de su boton. Relacionado con TD-071, que busca llegar al video y a las instrucciones de un training asignado sin duplicarlo.

AL DIA el 20-sep-2026: ya son TRES. El asistente anadio el de apagar el video (TD-146) porque hacerlo desde el editor costaba cuatro toques, y el usuario lo noto en el acto: 'unificar los tres iconos de la franja del player, ¿lo tienes registrado en un td?'.

Y son tres con TRES reglas de visibilidad distintas, que es lo que de verdad ensucia:
  - instrucciones (lista): no se dibuja si el ejercicio no tiene instrucciones;
  - editar (lapiz): no se dibuja si el training viene asignado;
  - video (camara): se dibuja SIEMPRE, en gris cuando no hay video, justamente para que los otros dos no se muevan de sitio al cambiar de ejercicio.

O sea que la franja puede tener uno, dos o tres iconos segun el ejercicio y el training, y las tres reglas se contradicen entre si. Cualquier unificacion tiene que decidir PRIMERO una sola regla de visibilidad; el numero de iconos es el sintoma.
- [ ] **TD-076** Volumen de los pitidos ajustable en Ajustes
  - El usuario oye los pitidos 'ligeramente mas alto que la musica' y quiere bajarlos un poco; recordaba que eso tenia un porcentaje. LO QUE HABIA: los pitidos de la corrida (AlarmPlayer.beepTone, desde WorkoutPlayerService playBeep y alarmCue) no aplicaban NINGUN volumen, asi que sonaban al 100 % del volumen multimedia, el mismo canal que Spotify. La curva perceptual en dB de AlarmPlayer existia, pero solo la usaba la vista previa de tonos del editor, y fijada a 1f, o sea tambien al 100 %. En el historial de este proyecto nunca hubo un campo de volumen en el modelo; el porcentaje que el usuario recordaba, si acaso, venia del app anterior. Y un comentario en ExerciseEditorScreen hablaba de 'el control de volumen y sonido del beep' cuando solo habia selector de sonido: de los que en un escaneo rapido hacen concluir cosas que no son; se corrigio y ahora remite al ajuste global. DECISION DEL USUARIO entre un nivel fijo mas bajo o un ajuste: el ajuste, para afinarlo el mismo en el telefono en vez de recompilar. LA SOLUCION: MasterConfig.beepVolume en %, default 100 -como sonaban-, guardado en SettingsStore, y en Ajustes -> Player un SegmentedRow con 100/85/70/55/40 % sobre la curva que ya existia (85 son unos -3 dB, 70 unos -5). Es uno solo para todos los pitidos, no por etapa. Al tocar un nivel suena un pitido a ese nivel, que es la unica forma de afinarlo contra la musica sin arrancar un training; se le pasa el nivel elegido en vez de leerlo de Ajustes porque se llama en el mismo toque que lo guarda. El servicio lee el nivel en CADA pitido y no al arrancar, para que un cambio con una corrida en marcha se note en el siguiente. La vista previa del editor tambien usa el nivel de Ajustes, para que no suene distinto que en el training. Los pitidos siguen sin pedir foco de audio: se oyen encima de la musica sin bajarla, que es lo que el usuario quiere.

### Fix

- [ ] **TD-156** Lo que salio de la primera semana con metros y feedback: carry alternado, respiro para contestar, preparacion de la caminata
  - REPORTADO el 24-sep, cuatro cosas de una vez:
(1) El usuario: "Carry demoro el doble, no me gusto". Con la revision 15 el carry iba por lados uno tras otro -tres viajes con la izquierda y despues tres con la derecha- con un minuto entre cada uno: cinco descansos en vez de los dos de antes.
(2) NIKO: en el cuello, la ultima direccion "no te da tiempo para marcar el feedback, la sesion simplemente termina". Un aguante no se contesta mientras se hace, y la ultima serie no tiene descanso detras.
(3) El usuario: en la caminata de LUMBAR no hay PREP, "apenas le doy al play, el tiempo ya esta corriendo".
(4) El usuario: "Minutes until it eased no lo veo marcado". La alarma guardo 24 minutos, y la pantalla final solo tiene botones 0/5/10/15/20/30/45/60: ninguno se encendia.

HECHO en codigo el mismo dia:
(1) Exercise.alternateSides: izquierda y derecha dentro de cada serie, sin descanso entre manos y con el descanso al cerrar la serie. El carry lo usa (LUMBAR_REVISION 16).
(2) Al final del training, un respiro de 10 s (StepEngine.ANSWER_SEC) si la ultima serie es por tiempo y pregunta como fue; lo pide el player con buildSteps(answerWindow = true), asi que ni los estimados ni los tests de estructura lo llevan. Entre ejercicios, la tarjeta que quedo sin contestar sale en la PREPARACION del siguiente.
(3) Las caminatas lumbares llevan 10 s de preparacion.
(4) FadeMinutes ensena el valor exacto ("Minutes until it eased: 24 min") cuando no es uno de los botones.

UN FALLO ENCONTRADO DE PASO, desde TD-147: al reubicar un paso tras editar a mitad de corrida, StepEngine comparaba por SERIE, y con lados la serie se repite en cada lado: estando en la serie 2 de la derecha podia volver a la serie 2 de la izquierda, ya hecha. Ahora cada paso lleva su PUESTO (PlayerStep.slot) en el orden en que se hace, que es lo que se compara. Y un ejercicio sin exerciseId no pregunta como fue: sin id no hay historial donde leerlo. AlternateSidesTest.
- [ ] **TD-100** Dos instancias del mismo ejercicio en un workout se funden en un registro
  - ENCONTRADO al arreglar TD-099 y levantado a peticion del usuario. SessionRecorder agrupa por ExerciseKey (exerciseId, workoutIndex), asi que si un workout repite el mismo ejercicio del catalogo, las dos apariciones escriben en la MISMA casilla: la segunda pisa las series de la primera y en el historial queda un solo registro, con el nombre y las series de la ultima. Se pierde la mitad del trabajo hecho. DONDE MUERDE HOY: es la razon por la que la plancha lateral de la rutina lumbar necesito dos entradas de catalogo, ex_side_plank_l y ex_side_plank_r (TD-086), en vez de una con nota 'cada lado' como hace el resto del catalogo. Se eligio asi a proposito para no perder las series de un lado, pero es rodear el fallo, no arreglarlo. EL FIX APARENTE: meter exerciseIndex en la clave, que desde TD-099 ya viaja en el registro. Dos apariciones del mismo ejercicio pasan a ser dos filas. LO QUE HAY QUE PENSAR ANTES, porque cambia como se cuenta el historial: - Con la clave nueva, un workout con 'Pushups' dos veces pasa de una fila a dos. Es mas fiel, pero es un cambio visible en trainings que el usuario ya tiene (MASTER repite ejercicios en varios sitios: comprobarlo antes). - ExerciseHistoryScreen agrupa por ejercicio a lo largo del tiempo; hay que ver si dos filas por sesion le estropean la serie o la mejoran. - Las sesiones ya guardadas no se pueden separar hacia atras: lo que se fundio, se fundio. - Si se arregla, la plancha lateral podria volver a ser UNA entrada de catalogo con nota, y el catalogo quedaria mas limpio. Eso seria un cambio aparte y posterior. Relacionado con TD-101, que es el que decide que se puede tocar del historial y que no.
- [ ] **TD-064** Fix: quitar rotativo a un workout borra todas las variantes menos la primera
  - makeWorkoutSimple conserva los ejercicios de la PRIMERA variante y descarta el resto: w.copy(rotating = false, exercises = w.variants.firstOrNull()?.exercises, variants = emptyList()). Un workout rotativo con 3 variantes pierde dos sin aviso y sin deshacer. EL COMPORTAMIENTO DESEADO, en palabras del usuario: 'rotativo es que los workouts rotan, si le quito el rotativo deberia simplemente no rotar, no borrar nada'. O sea que el flag gobierna COMO se recorren las variantes, no DONDE viven los ejercicios; quitarlo no puede ser una operacion destructiva. Se descarto anadir un dialogo de confirmacion: confirmar una perdida de datos no deseada no arregla que la perdida no deba ocurrir. El bug lleva ahi desde que existe la funcion y no se habia notado. NO ES SOLO ESTE FIX: al revisarlo el usuario pregunto 'cual es la interfaz para hacer de un workout una variante, no la ubico', y esa pregunta abre el modelo entero de workout/variante, que hoy tiene dos representaciones distintas para lo mismo (exercises sueltos cuando es simple, exercises dentro de variants cuando es rotativo) y es de donde nace la perdida de datos al convertir. Revisar el modelo y los flujos de conversion en su propio espacio antes de tocar codigo. CASO DE REFERENCIA, senalado por el usuario: el rotativo 'Strength' del training MASTER funciona como se espera y es el que hay que mirar al abordarlo. Precision de vocabulario: el usuario lo describe como 'dentro de Strength hay 2 workouts, uno lower y otro upper, cada uno con sus ejercicios independientes', pero en el modelo Strength es UN workout con rotating=true y dos VARIANTES, Lower (12 ejercicios) y Upper (5). Lo que el usuario llama workout ahi es lo que el codigo llama variante, y esa distancia entre el vocabulario del usuario y el del modelo es parte de lo que hay que resolver. Comprobado en sus datos del 30-ago-2026: MASTER tiene tambien 'Cardio' rotativo con 4 variantes (Rope Jumping, Tire Jumping, Shadow Boxing, Running), donde quitar el rotativo hoy borraria tres. Con Strength borraria los 5 ejercicios de Upper.

### Mantenimiento

- [ ] **TD-071** Llegar al video e instrucciones de un training asignado sin duplicarlo
  - A la ficha de video e instrucciones (ExerciseMediaCard) no se llega desde un training asignado. Vive solo dentro de ExerciseEditorScreen, y a ese se entra por Edit -> workout -> ejercicio; un training asignado no ofrece Edit, solo Duplicate. El usuario ya tiene salida -duplicar el training y editar la copia- y le parece bien la regla, asi que esto no bloquea a nadie. Pero queda anotado porque es dano colateral: esa regla existe para proteger la ESTRUCTURA del training, que la sincronizacion si pisa, y el video y las instrucciones no corren ese riesgo porque viven aparte, por exerciseId del catalogo, y la sincronizacion no los toca nunca. Si algun dia molesta, el sitio natural es la vista previa: tocar un training asignado ya abre PreviewView con sus workouts y ejercicios, y desde ahi se podria entrar al material de cada uno sin reabrir la edicion. Salio al revisar TD-070.

## Hechos

### Branding

- [x] **TD-008** Rebrand del texto del wordmark (opcional)

### Bug

- [x] **TD-155** McGill sale "Partial" en el historial con todo hecho
- [x] **TD-154** Apagar el video es una preferencia del telefono, no del training
- [x] **TD-153** El lado del ejercicio se corta en el player: DERECHA sale HA
- [x] **TD-075** Fix: el video del ejercicio pausa la musica (Spotify) al reproducirse
- [x] **TD-015** Fix drag-reorder en lista de trainings

### Feature

- [x] **TD-158** La alarma sale de Settings: su propio icono "Morning", su pantalla y el dolor en el calendario
- [x] **TD-152** Los ejercicios con el peso del cuerpo tampoco dicen si costaron
- [x] **TD-147** El ejercicio unilateral: el lado entra en el modelo
- [x] **TD-145** El video es un campo mas del ejercicio: una sola tarjeta y sin carteles
- [x] **TD-139** Las instrucciones dejan de viajar en el APK: una tabla por exerciseId
- [x] **TD-140** Publicar un video desde el telefono: bucket en Supabase y boton en la ficha del ejercicio
- [x] **TD-141** Migrar los 7 videos publicados y retirar videos.json
- [x] **TD-138** Archivar trainings: la lista ensena lo que toca, no todo lo que existe
- [x] **TD-136** Semana base de NIKO, sembrada un dia a la vez
- [x] **TD-132** Una revision de una rutina ya asignada no le llega al atleta hasta reasignarla
- [x] **TD-131** Las instrucciones del catalogo viajan con el app, a todos los telefonos
- [x] **TD-130** El descanso dibuja lo que hay que cargar: discos por lado y mancuernas
- [x] **TD-127** Rutina de NIKO orientada a Muay Thai: primer dia sembrado
- [x] **TD-126** El historial de un atleta asignado sube al coach
- [x] **TD-125** Medir el dolor al despertar, que es el que lleva anios
- [x] **TD-124** La caminata registra a que velocidad fue
- [x] **TD-122** El feedback del peso se pregunta en el descanso, y los huecos se cierran al terminar
- [x] **TD-123** Revision 6 de la rutina: sube el bloque de cadera entero
- [x] **TD-118** El historial en corto: la serie en una linea y el feedback en icono
- [x] **TD-116** El control NEXT dice el peso de la siguiente serie
- [x] **TD-112** Revision 4 de la rutina: el puente sube a 31 kg y la caminata corta lleva su velocidad
- [x] **TD-110** El historial ensena el dolor y la nota de cada sesion
- [x] **TD-105** Revision 2 de la rutina, y corregir los pesos mal registrados del 15-sep
- [x] **TD-104** Inventariar el equipo disponible para poder disenar con lo que hay
- [x] **TD-098** Cargar el bloque de cadera y gluteo, que se le queda corto
- [x] **TD-091** Las indicaciones de la caminata dicen de que protege la regla de la primera hora
- [x] **TD-090** Anotar en el historial la sesion del 13-sep que se hizo sin el app
- [x] **TD-089** Registrar como se sintio la sesion, no solo que series se hicieron
- [x] **TD-088** Variante de dia malo: la movilidad antes de la caminata
- [x] **TD-086** El training LUMBAR se siembra desde el codigo, no se importa a mano
- [x] **TD-085** Series con trabajo y descanso propios: la piramide descendente en un solo ejercicio
- [x] **TD-084** Los videos viajan con la asignacion, sin asignarlos a mano
- [x] **TD-080** Que ve un ejercicio que todavia no tiene video
- [x] **TD-079** El chrome del player se desvanece solo y deja el video limpio
- [x] **TD-078** El video del player se queda con la pantalla (reloj y controles superpuestos)
- [x] **TD-077** Como reproduce Freeletics sus videos: zoom, loop parcial y velocidad
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
- [x] **TD-044** Feedback haptico en el player (skip, check, pause)
- [x] **TD-043** Preview del siguiente ejercicio en REST
- [x] **TD-042** Resumen de stats en Historial
- [x] **TD-041** Ring de progreso en el player
- [x] **TD-040** La tarjeta dice cuantos ejercicios y cuanto dura
- [x] **TD-039** Swipe-to-reveal en TrainingCards, en reemplazo del menu de 3 puntos
- [x] **TD-037** Tap en dia del calendario abre sheet con sesiones de ese dia
- [x] **TD-035** Atenuar pantalla del player cuando el timer esta en pausa
- [x] **TD-028** Skip por ejercicio en el player
- [x] **TD-021** Historial por ejercicio + sesiones parciales
- [x] **TD-014** Keep-screen-on durante la corrida (opcional)
- [x] **TD-013** Accesibilidad (opcional)
- [x] **TD-012** Onboarding / estado vacio (opcional)
- [x] **TD-011** Animaciones de ejercicio (opcional)
- [x] **TD-010** Catalogo de ejercicios expandido (opcional)
- [x] **TD-009** Export/import de datos a JSON (PRIORITARIO)

### Fix

- [x] **TD-146** Apagar el video desde el player, y que lo editado en caliente se vea ya
- [x] **TD-142** Fix: republicar desde el arranque leia isCoach antes de que existiera
- [x] **TD-134** Fix: borrar un training dejaba su asignacion viva, y no habia donde quitarla
- [x] **TD-129** Fix: la velocidad de la caminata no llegaba al registro
- [x] **TD-128** Fix: un training recien sembrado no se podia asignar hasta reiniciar el app
- [x] **TD-121** Fix: borrar una sesion, y las correcciones al arrancar, no escriben respaldo
- [x] **TD-120** Escribir en la sesion del 17-sep el feedback que el app boto
- [x] **TD-119** Borrar una sesion del historial solo se desliza con la tarjeta cerrada
- [x] **TD-117** Fix: lo que se marca en 'How did the weight feel?' nunca llega al historial
- [x] **TD-115** El boton central no desaparece en los ejercicios por reps: se apaga
- [x] **TD-114** Fix: el indicador de refrescar salta al desplazarse y se queda girando
- [x] **TD-113** Fix: la pregunta del dolor se dibuja encima de la ultima tarjeta del training
- [x] **TD-108** La rutina lumbar solo se siembra en el telefono de su dueno
- [x] **TD-106** Fix: la tarjeta del peso se quedo sin sus botones al crecer la nota
- [x] **TD-102** Reordenar las dos sesiones lumbares que quedaron en alfabetico
- [x] **TD-099** Fix: el historial ordena los ejercicios por nombre y no por la rutina
- [x] **TD-092** Fix: la nota del ejercicio se dibuja una linea encima de otra
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

- [x] **TD-135** El codigo y la documentacion dicen lo que dice la pantalla
- [x] **TD-103** La rutina va por revision, no por una migracion en cada ajuste
- [x] **TD-093** Permisos por prefijo, para que los scripts dejen de pedir permiso
- [x] **TD-087** Borrar los cinco ejercicios propios que quedaron sin usar
- [x] **TD-083** Auditoria de coherencia UI: un solo mecanismo para todas las listas
- [x] **TD-072** Ajustes: ensenar y liberar el espacio de los videos descargados
- [x] **TD-069** Pendiente: comprobar en dispositivo los avisos y la entrega automatica
- [x] **TD-066** Opcional: script para publicar perfiles y asignaciones desde la PC
- [x] **TD-056** Borrar handoff.md (TD-048 resuelto)
- [x] **TD-055** build-debug.ps1 deja de copiar el APK a Download del telefono
- [x] **TD-054** Regla: verificar firma antes de instalar y nunca desinstalar sin autorizacion
- [x] **TD-047** Documentacion: regla post-build en AGENTS.md + actualizar master-forge.md
- [x] **TD-033** Arquitectura: Repository interfaces + MVI + Navigation + Testing
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

- [x] **TD-157** La alarma confirma lo que se toco: vibracion, el numero en grande y "Change"
- [x] **TD-150** Los trainings archivados tambien se ordenan arrastrando
- [x] **TD-109** La escala de dolor describe cada numero, no solo los extremos
- [x] **TD-107** El centro del player cede sitio a la nota: contador junto a las reps y tarjeta de peso translucida
- [x] **TD-074** Nombre del ejercicio en el player: dos lineas como mucho, sin cortar palabras y con alto fijo
