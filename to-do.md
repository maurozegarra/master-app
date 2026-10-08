# To-Do - MASTER

> Generado automaticamente por `forge-status.ps1` desde `docs/forge-todo.json`.
> No editar directamente; actualizar el JSON y regenerar con `.\forge-status.ps1`.
> Convencion de commits: `feat: TD-XXX ...` / `fix: TD-XXX ...`.

Progreso: **171 / 193** hechos, 21 pendientes, 1 descartados.

## Pendientes

### Deseable

- [ ] **TD-166** Circuitos en el editor de workouts
  - DESEABLE, no prioridad, decidido por el usuario el 25-sep: "cada vez lo veo mas improbable que yo entre y cree un Training". Los trainings entran desde el codigo (el coach los arma), y Workout.circuit (TD-137) ya funciona asi. Seria un interruptor "Circuit" en el editor del workout, junto al de rotativo.
- [ ] **TD-133** El app en español, para NIKO y para cualquiera que no lea ingles
  - PEDIDO el 19-sep-2026: 'niko no maneja el ingles, lo ideal seria que el app tenga soporte para español, apuntalo como un TD'. Para salir del paso ese dia se pusieron en español las instrucciones del catalogo (TD-131) y el texto libre de su rutina (TD-127 revision 3).

LO QUE FALTA: el app es English-only por decision de producto (I18n.get() devuelve EN y lang() es 'en'), pero la base existe: Strings es una clase con todas las cadenas y el catalogo ya tiene los nombres en los dos idiomas. Hace falta un Strings ES completo, un ajuste de idioma por telefono (no por training: el idioma es de quien lo usa), y decidir que pasa con los textos libres que escribe el coach -notas, nombres de bloques-, que no se traducen solos.

OJO: el idioma es del TELEFONO. Si el coach escribe en ingles para si y en español para NIKO, las notas de sus rutinas tienen que ir en el idioma de quien las recibe.

DESEABLE, no prioridad, decidido por el usuario el 23-sep: "la he visto bastante comoda a Niko, con las instrucciones en espanol le va bastante bien". Cuando se haga, ojo: Strings esta al tope de 255 parametros de la JVM y hay que reorganizarla en bloques antes de tener dos idiomas.

### Feature

- [ ] **TD-190** Agua: registrar lo que tomo, con recordatorios que se adaptan a lo que de verdad tomé
  - PEDIDO el 4-oct: "un componente más, similar a la alarma, para registrar mi consumo de agua", con "inteligencia". Lo que le molesta de su app actual (captura del 4-oct): recuerda por horario fijo (desde las 7, cada hora) aunque se levante a las 9:50 y acabe de tomar; no acelera cuando va atrasado; sigue recordando tras una botella de 600 aunque vaya adelante; y después de anotar 600 sugiere 600, cuando su vaso es de 200.

DECIDIDO con él: meta fija de 2 litros; dejar de recordar 2 horas antes de dormir; solo agua en la primera versión; medidas vaso 200 y botella 600.

PLAN: (1) LA REGLA: una curva ideal reparte los 2 litros en parejo desde que despierta hasta la hora de cortar. El próximo recordatorio sale cuando va atrasado un vaso (200 ml) respecto de esa curva, nunca antes de 30 min desde el último trago. Resuelve los tres casos: tras tomar no avisa enseguida; con déficit avisa cada 30 min hasta ponerse al día; con superávit espera hasta que la curva lo alcance. (2) EL DÍA empieza al contestar la alarma (MorningEntry.answeredAt); sin alarma, con el primer vaso anotado. CORTA 2 h antes de acostarse: la hora de acostarse sale de la próxima alarma menos las horas de sueño del aviso para dormir (Bedtime.bedBy). Sin alarma al día siguiente, a definir. (3) ANOTAR: el + anota siempre 200 ml de un toque; botones de 200 y 600; la notificación trae +200 y +600 sin abrir el app; lista del día con borrar deslizando y confirmación. (4) DÓNDE: ícono de gota en la barra de MASTER junto al despertador, con su pantalla: avance contra la meta, la curva ideal contra lo tomado, la lista y el + en la barra; con los componentes del app (SettingsScaffold con subtítulo, StatTile, LineChartCard, listCard, SwipeActionsRow). (5) Módulo aparte como la alarma, con su almacén y en el respaldo. La regla, pura y con tests.

HECHO e iterado el 4-oct (v1.0.385 a 1.0.390): módulo water/ (Water.kt puro con WaterPlan y Drinks, WaterStore, WaterAlarm, WaterReceiver, WaterHome, WaterParts), gota en la barra de MASTER, respaldo formato 6, tests en WaterPlanTest. Ajustes pedidos por él: pantalla siempre encendida; "Next" con la hora como "5:23" y "PM" chico; el ritmo en vasos y no en ml ("1668 ml" cambiaba cada minuto); BEBIDAS: agua, creatina, quinua, sopa, gaseosa y café, todas cuentan igual, cada una con su ícono y su color de STAGE_COLORS en la lista ("con sólo un vistazo sé qué he tomado"); anotar con otra hora ("Other") y corregir tocando la fila, con el reloj de 12 h; las ocho tomas del 4-oct de su app de antes, cargadas una vez solo ese día.

REGLA CORREGIDA (v1.0.390): la primera esperaba a ir un vaso entero atrás de la curva (de 9:50 daba 11:52; de 17:49, nada), y se le había dicho "alrededor de una hora". Él lo marcó con sus ejemplos (9:50 -> 10:30, 17:49 -> 18:30). Ahora: 30 min después del último trago llevado a la siguiente :00 o :30; si va adelante, cuando la curva lo alcanza, también a :00 o :30; y si así ya no cabe y falta la meta, un último aviso 30 min antes del corte. Sin alarma al día siguiente corta a las 19:00 (decisión suya).

REGLA CAMBIADA el 4-oct (v1.0.391), a pedido suyo: la curva pareja desde que despierta no es como toma. Ahora es su HORARIO FIJO. Casa: 200 ml a las 7, 8, 9, 10, 11 y 12, y a las 14, 15, 16 y 17 (a la 1 no, va con la barriga llena). Presencial (alarma antes de las 6): 5:00 creatina 200, 6:30 desayuno 200 (maca, quinua o café), 9:00 botella 600, 12:30 sopa + limonada del menú 400, 14:00 segunda botella 600 (la compra 1:50). Al día: avisa a la próxima hora del horario que no esté cubierta (una botella cubre varias). Con un vaso o más de déficit: cada 30 min desde el último trago, en punto o y media, incluida la 1. Pasada la última hora: cada 30 min. Un último aviso 30 min antes del corte. El horario se elige por la alarma de hoy y no por el training corto, porque el corto va también los domingos. BEBIDAS nuevas: maca y limonada.

AJUSTES del 4-oct (v1.0.392 a 1.0.395): el diálogo de bebida, compacto (la hora en el título; 200 · 600 · Other, y el campo solo con Other); agua, gaseosa, café y maca con colores vivos propios, porque los de la paleta se apagaban; y los títulos de TODOS los diálogos del app a 20 sp en negrita, desde la tipografía del tema, porque quedaban más grandes que el título de la pantalla.

EN USO, 5-oct (primer día presencial): 2,100 ml, casi exacto al horario de oficina. RARO por la tarde: anotó 500 (no 600) a las 14:02 y quedó en 1,900, ya sin horas del horario por delante; la regla de 'pasada la última hora, cada 30 min' le avisó a las 15:00 y siguió insistiendo cada media hora por solo 100 ml hasta su vaso de las 19:40. Por medio vaso no debería insistir así. Él recuerda otra observación de esa tarde; se ve al usarlo. PENDIENTE de decidir con él, no tocado.

RESUELTO el 6-oct (v1.0.404), con su lógica: la botella no vence entera a su hora, se va tomando. Presencial: las botellas van en vasos por hora (9, 10, 11 y 14, 15, 16). Pasado el horario la cadencia sigue, un vaso por hora hasta cortar, así que lo que falta se pide a la hora siguiente y no cada media hora. Su caso del lunes, 500 ml a las 14:02 con 1,900: Next 17:00. Con un vaso o más de atraso sigue cada 30 min.

AJUSTES del 8-oct (v1.0.420 a 1.0.428), con él: avisos en :00/:30 y nunca a menos de 15 min del último trago (10:45 → 11:00, 10:46 → 11:30); cualquier hora del horario sin cubrir avisa. Cada hora tiene su bebida y se anota con ella: la primera del día creatina, 12:30 sopa, 13:00 limonada, el desayuno repite el último. Botellas del día presencial: con gas 600 a las 9 (Sparkling) y sin gas 500 a las 14 (Bottle), dosificadas por tercios; los 100 ml que faltan se piden en casa a las 20:00. Corte 1 h 30 antes de dormir. Botón Bottle en vez de 600 ml. 'Pace' dibuja la botella con lo que queda (2/3, 1/3, vacía), contando desde la hora en punto más cercana. Arreglado: dos tomas en el mismo minuto tumbaban la pantalla. PENDIENTE: la botella de 'Pace' puede ser más grande, hay espacio en la tarjeta.
- [ ] **TD-185** Avatar por perfil: en el selector Me / NIKO y donde aparezca su nombre
  - SEPARADO de TD-169 el 3-oct-2026, a pedido del usuario: "el avatar después". A DECIDIR: foto propia o iniciales con color.
- [ ] **TD-159** Alarma: etiquetas de la noche, para cruzarlas con el dolor de la manana
  - PROPUESTO el 24-sep y registrado el 25 (salio de TD-158, donde solo quedaba mencionado).

POR QUE, y es la de mas valor de las cinco: el 19-sep su mejor dato vino de la NOCHE y no del ejercicio -colchon rotado y almohada bajo las rodillas: cinco horas sin dolor-, y la noche siguiente empeoro por dormir de lado cuando NIKO se paso a su cama. Nadie va a recordar eso a mano semanas despues.

QUE: tras contestar el dolor en la alarma, etiquetas OPCIONALES de un toque -almohada en las rodillas, dormi de lado, cena tarde, entrene tarde, otra cama, dormi poco-, guardadas en la manana del dia. En la pantalla Morning, con unas semanas de datos: el dolor medio con y sin cada etiqueta ("con almohada 1.2, sin ella 2.8").

A DECIDIR: la lista de etiquetas -la propone el coach, la ajusta el usuario-; si se contestan en la misma pantalla de la alarma o despues, en Morning, para no alargar el primer toque del dia.
- [ ] **TD-161** Alarma: sonido que sube de a poco
  - PROPUESTO el 24-sep, registrado el 25. Que el volumen empiece bajo y llegue al maximo en unos 30 s, en vez de arrancar al maximo. Es un despertador para alguien con dolor lumbar: un sobresalto al despertar no ayuda.
- [ ] **TD-162** Alarma: control rapido en el panel de Android
  - PROPUESTO el 24-sep, registrado el 25. Un Quick Settings tile para encender, apagar o saltar la alarma de manana sin abrir el app.
- [ ] **TD-163** Alarma: widget con la proxima alarma y las ultimas mananas
  - PROPUESTO el 24-sep, registrado el 25. En la pantalla de inicio: la hora de la proxima alarma y el dolor de las ultimas 7 mananas, con su color y su numero.
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
- [ ] **TD-096** Poder borrar un ejercicio propio desde la app
  - EL HUECO, encontrado al hacer TD-087: **el app sabe crear ejercicios propios y no sabe borrarlos**. No hay UI, no hay metodo en MasterViewModel, no hay nada: addCustomExercise() los agrega y ahi se quedan para siempre. LA CONSECUENCIA: cada intento de armar algo a mano deja basura en el selector de ejercicios para siempre. Al usuario le quedaron cinco del intento de armar la rutina lumbar y hubo que borrarlos con una migracion de codigo (TD-087), que es una respuesta desproporcionada para algo que deberia ser un swipe. LO QUE HAY QUE RESOLVER, y por eso no es solo agregar un boton: que pasa con un ejercicio propio que SI esta usado en algun training. Borrarlo dejaria a esas instancias sin nombre de catalogo y el player ensenaria el id crudo (ex_curl_up). Opciones: no dejar borrarlo y decir donde se usa; borrarlo y congelar el nombre en cada instancia, que es lo que ya hace ExerciseCatalog.display() con su fallback; o pedir confirmacion nombrando los trainings afectados. La segunda es la que encaja con como esta hecho hoy. Tambien hay que decidir que pasa con su video propio y sus instrucciones, que viven por exerciseId. Relacionado con TD-010 (catalogo expandido) y con el swipe-to-reveal que ya usan las otras listas (TD-039, TD-057).
- [ ] **TD-081** Unificar los tres iconos de la franja del player (instrucciones, editar y video)
  - NOTA DEL USUARIO, sin decidir todavia: 'no me convence del todo un boton para instrucciones y otro boton para editar el ejercicio, creo que deberiamos unificarlo o pensar en algo mas'. Hoy la franja de rutina lleva dos iconos sueltos a la derecha: InstructionsButton (lista, abre un ModalBottomSheet con los pasos numerados; no se dibuja si el ejercicio no tiene instrucciones) y EditExerciseButton (lapiz, llama a editRunningExercise; no se dibuja si el training viene asignado). O sea que segun el ejercicio y el training puede haber dos iconos, uno o ninguno, y la franja cambia de contenido sin que el usuario sepa por que. A PENSAR: un solo punto de entrada -menu, sheet unico con pestanas, o accion contextual- en vez de dos iconos que compiten por la misma esquina. Contexto: surge tras descartar las instrucciones como relleno del hueco del video (TD-080), que las devuelve a vivir detras de su boton. Relacionado con TD-071, que busca llegar al video y a las instrucciones de un training asignado sin duplicarlo.

AL DIA el 20-sep-2026: ya son TRES. El asistente anadio el de apagar el video (TD-146) porque hacerlo desde el editor costaba cuatro toques, y el usuario lo noto en el acto: 'unificar los tres iconos de la franja del player, ¿lo tienes registrado en un td?'.

Y son tres con TRES reglas de visibilidad distintas, que es lo que de verdad ensucia:
  - instrucciones (lista): no se dibuja si el ejercicio no tiene instrucciones;
  - editar (lapiz): no se dibuja si el training viene asignado;
  - video (camara): se dibuja SIEMPRE, en gris cuando no hay video, justamente para que los otros dos no se muevan de sitio al cambiar de ejercicio.

O sea que la franja puede tener uno, dos o tres iconos segun el ejercicio y el training, y las tres reglas se contradicen entre si. Cualquier unificacion tiene que decidir PRIMERO una sola regla de visibilidad; el numero de iconos es el sintoma.

### Fix

- [ ] **TD-100** Dos instancias del mismo ejercicio en un workout se funden en un registro
  - ENCONTRADO al arreglar TD-099 y levantado a peticion del usuario. SessionRecorder agrupa por ExerciseKey (exerciseId, workoutIndex), asi que si un workout repite el mismo ejercicio del catalogo, las dos apariciones escriben en la MISMA casilla: la segunda pisa las series de la primera y en el historial queda un solo registro, con el nombre y las series de la ultima. Se pierde la mitad del trabajo hecho. DONDE MUERDE HOY: es la razon por la que la plancha lateral de la rutina lumbar necesito dos entradas de catalogo, ex_side_plank_l y ex_side_plank_r (TD-086), en vez de una con nota 'cada lado' como hace el resto del catalogo. Se eligio asi a proposito para no perder las series de un lado, pero es rodear el fallo, no arreglarlo. EL FIX APARENTE: meter exerciseIndex en la clave, que desde TD-099 ya viaja en el registro. Dos apariciones del mismo ejercicio pasan a ser dos filas. LO QUE HAY QUE PENSAR ANTES, porque cambia como se cuenta el historial: - Con la clave nueva, un workout con 'Pushups' dos veces pasa de una fila a dos. Es mas fiel, pero es un cambio visible en trainings que el usuario ya tiene (MASTER repite ejercicios en varios sitios: comprobarlo antes). - ExerciseHistoryScreen agrupa por ejercicio a lo largo del tiempo; hay que ver si dos filas por sesion le estropean la serie o la mejoran. - Las sesiones ya guardadas no se pueden separar hacia atras: lo que se fundio, se fundio. - Si se arregla, la plancha lateral podria volver a ser UNA entrada de catalogo con nota, y el catalogo quedaria mas limpio. Eso seria un cambio aparte y posterior. Relacionado con TD-101, que es el que decide que se puede tocar del historial y que no.
- [ ] **TD-064** Fix: quitar rotativo a un workout borra todas las variantes menos la primera
  - makeWorkoutSimple conserva los ejercicios de la PRIMERA variante y descarta el resto: w.copy(rotating = false, exercises = w.variants.firstOrNull()?.exercises, variants = emptyList()). Un workout rotativo con 3 variantes pierde dos sin aviso y sin deshacer. EL COMPORTAMIENTO DESEADO, en palabras del usuario: 'rotativo es que los workouts rotan, si le quito el rotativo deberia simplemente no rotar, no borrar nada'. O sea que el flag gobierna COMO se recorren las variantes, no DONDE viven los ejercicios; quitarlo no puede ser una operacion destructiva. Se descarto anadir un dialogo de confirmacion: confirmar una perdida de datos no deseada no arregla que la perdida no deba ocurrir. El bug lleva ahi desde que existe la funcion y no se habia notado. NO ES SOLO ESTE FIX: al revisarlo el usuario pregunto 'cual es la interfaz para hacer de un workout una variante, no la ubico', y esa pregunta abre el modelo entero de workout/variante, que hoy tiene dos representaciones distintas para lo mismo (exercises sueltos cuando es simple, exercises dentro de variants cuando es rotativo) y es de donde nace la perdida de datos al convertir. Revisar el modelo y los flujos de conversion en su propio espacio antes de tocar codigo. CASO DE REFERENCIA, senalado por el usuario: el rotativo 'Strength' del training MASTER funciona como se espera y es el que hay que mirar al abordarlo. Precision de vocabulario: el usuario lo describe como 'dentro de Strength hay 2 workouts, uno lower y otro upper, cada uno con sus ejercicios independientes', pero en el modelo Strength es UN workout con rotating=true y dos VARIANTES, Lower (12 ejercicios) y Upper (5). Lo que el usuario llama workout ahi es lo que el codigo llama variante, y esa distancia entre el vocabulario del usuario y el del modelo es parte de lo que hay que resolver. Comprobado en sus datos del 30-ago-2026: MASTER tiene tambien 'Cardio' rotativo con 4 variantes (Rope Jumping, Tire Jumping, Shadow Boxing, Running), donde quitar el rotativo hoy borraria tres. Con Strength borraria los 5 ejercicios de Upper.

### Mantenimiento

- [ ] **TD-071** Llegar al video e instrucciones de un training asignado sin duplicarlo
  - A la ficha de video e instrucciones (ExerciseMediaCard) no se llega desde un training asignado. Vive solo dentro de ExerciseEditorScreen, y a ese se entra por Edit -> workout -> ejercicio; un training asignado no ofrece Edit, solo Duplicate. El usuario ya tiene salida -duplicar el training y editar la copia- y le parece bien la regla, asi que esto no bloquea a nadie. Pero queda anotado porque es dano colateral: esa regla existe para proteger la ESTRUCTURA del training, que la sincronizacion si pisa, y el video y las instrucciones no corren ese riesgo porque viven aparte, por exerciseId del catalogo, y la sincronizacion no los toca nunca. Si algun dia molesta, el sitio natural es la vista previa: tocar un training asignado ya abre PreviewView con sus workouts y ejercicios, y desde ahi se podria entrar al material de cada uno sin reabrir la edicion. Salio al revisar TD-070.

### UI

- [ ] **TD-192** La semana de la pantalla principal marca qué training toca cada día
  - PEDIDO el 5-oct, mirando la pantalla principal. Los días futuros de la semana son círculos vacíos: el martes no dice que va LUMBAR, ni el miércoles que va el short. El puntito de abajo solo marca lo ya entrenado. La información ya existe: scheduleDays y scheduleDates de cada training (TD-167, TD-178), que es lo que decide el orden de la lista.

A DEFINIR con él antes de tocar: cómo se marca (inicial, color del training, ícono) sin quitarle protagonismo al puntito de hecho, y qué se ve cuando dos trainings caen el mismo día.
- [ ] **TD-193** Un solo formato de hora en todo el app (24 h o AM/PM)
  - VISTO el 5-oct en la pantalla Morning. El app mezcla formatos: Morning va en 24 h ("23:30", "5:00"), el agua en 12 h ("5:23 PM", puesto el 4-oct) y el historial dice "8:29 AM". A DEFINIR con él cuál va en todo el app; después, un formateador compartido en util/Format.kt en vez de un DateTimeFormatter en cada pantalla.
- [ ] **TD-188** La barra de arriba a 56 dp, para igualar el aire bajo el wordmark al de TickTick (sine die)
  - PEDIDO el 4-oct, comparando con TickTick: entre el wordmark y los días de la semana había ~35 dp contra ~28 de "October" a "Mon" en TickTick. Se quitaron los 4 dp de arriba de la lista (v1.0.379): quedan ~31 dp.

LO QUE FALTA: el resto es el aire de adentro de la TopAppBar de Material, que en material3 1.2.1 (BOM 2024.06) mide 64 dp fijos y centra el título, sin parámetro para cambiarlo. Las versiones nuevas aceptan expandedHeight: actualizar el BOM de Compose y poner 56 dp lo dejaría en ~27 dp. Es una línea, pero la actualización afecta a todo el app y hay que revisar las pantallas principales con capturas. Desplazar la semana hacia arriba la recorta (la lista recorta su borde), y una barra propia sería dibujar un control a mano: descartados.

SINE DIE, decisión del usuario el 4-oct: "mucho trabajo para hoy".

## Descartados

- [-] **TD-094** Publicar los videos de los nueve ejercicios del lumbar
  - LOS NUEVE MOVIMIENTOS que entraron al catalogo con TD-086 -ex_walk, ex_hip_hinge, ex_curl_up, ex_side_plank_l, ex_side_plank_r, ex_bird_dog, ex_glute_bridge, ex_suitcase_carry, ex_box_squat- no tienen video. El gato-camello si: usa ex_cat_cow, que ya estaba publicado desde TD-062. EL CAMINO, que es el unico automatizable y esta documentado en AGENTS.md ('Como poner contenido en el telefono'): renombrar cada mp4 a <exerciseId>.mp4, subirlo con 'gh release upload videos', agregar su entrada a videos.json con file, rev y bytes, y hacer push de videos.json a main. El app lee el manifiesto de GitHub raw y baja cada video la primera vez que hace falta. **No hay que publicar version nueva del app.** PENDIENTE DEL USUARIO: pasar los archivos. Los que ya estan publicados pesan entre 1.7 y 4.8 MB, que es la escala que conviene. DETALLE UTIL: si solo hay un clip de plancha lateral, las entradas de ex_side_plank_l y ex_side_plank_r pueden apuntar al mismo archivo; el app lo baja una vez por cada id. OJO: el push de videos.json necesita autorizacion explicita del usuario, como cualquier push.

DESCARTADO el 6-oct-2026, decisión suya: "el tema de los videos, cero ganas de atenderlo". Quedaron 6 de 9 publicados; faltan la bisagra de cadera y las dos planchas laterales.

## Hechos

### Branding

- [x] **TD-008** Rebrand del texto del wordmark (opcional)

### Bug

- [x] **TD-155** McGill sale "Partial" en el historial con todo hecho
- [x] **TD-154** Apagar el video es una preferencia del telefono, no del training
- [x] **TD-153** El lado del ejercicio se corta en el player: DERECHA sale HA
- [x] **TD-149** Borrar una sesion en el telefono del atleta no la borra del servidor
- [x] **TD-075** Fix: el video del ejercicio pausa la musica (Spotify) al reproducirse
- [x] **TD-015** Fix drag-reorder en lista de trainings

### Feature

- [x] **TD-178** El coach asigna un training para una fecha, y la lista lo resalta
- [x] **TD-175** La alarma como despertador: varias alarmas en tarjetas, y fuera del lanzador
- [x] **TD-174** Probar un training sin que quede registrado: modo prueba en el player
- [x] **TD-171** La voz de los pitidos, por telefono: cada uno suena distinto al entrenar juntos
- [x] **TD-169** Historial del peso y la cintura, el propio y el de NIKO, con el pesaje del sábado en el app
- [x] **TD-167** El training que sigue va primero en la lista, con un destello en el borde
- [x] **TD-164** Corregir el dolor de la manana desde la pantalla Morning
- [x] **TD-165** Dolor habitual y dolor de crisis, separados: la sesion ya no pregunta por la crisis
- [x] **TD-160** Alarma: aviso para ir a dormir, calculado desde la hora de la alarma
- [x] **TD-158** La alarma sale de Settings: su propio icono "Morning", su pantalla y el dolor en el calendario
- [x] **TD-152** Los ejercicios con el peso del cuerpo tampoco dicen si costaron
- [x] **TD-151** El dolor se anota cuando pasa, no al terminar el training
- [x] **TD-147** El ejercicio unilateral: el lado entra en el modelo
- [x] **TD-145** El video es un campo mas del ejercicio: una sola tarjeta y sin carteles
- [x] **TD-143** Antes de repartir, el app dice que le va a llegar incompleto
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
- [x] **TD-097** Caminata de cierre en la variante de dia malo
- [x] **TD-095** Modo distancia: ejercicios que se miden en metros
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
- [x] **TD-076** Volumen de los pitidos ajustable en Ajustes
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

- [x] **TD-191** Sign out del coach con confirmación, y el historial avisa cuando está fuera de sesión
- [x] **TD-186** Fix: el aviso para ir a dormir salía aunque ya se hubiera tocado "Going to bed"
- [x] **TD-184** Fix: el telefono del coach publicaba sus instrucciones del lumbar, en ingles, y le llegaban a NIKO
- [x] **TD-176** Los minutos hasta aflojar: una sola fuente, y que se puedan corregir
- [x] **TD-172** En un circuito, el ejercicio que pasa directo al siguiente nunca pregunta como fue
- [x] **TD-156** Lo que salio de la primera semana con metros y feedback: carry alternado, respiro para contestar, preparacion de la caminata
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

- [x] **TD-170** Quitar el ajuste "Leading zeros in clock"
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

- [x] **TD-189** Start de 40 dp y solo con borde, y la pantalla previa con barra: la prueba como ojo y el resumen como subtítulo
- [x] **TD-187** Un solo campo de texto en todo el app (AppTextField), en vez de once copiados a mano
- [x] **TD-183** El reloj para elegir una hora, en 12 horas y con los colores del app
- [x] **TD-182** Morning: agregar una alarma con un + en la barra, no con un boton a todo lo ancho
- [x] **TD-181** El detalle del historial dice al menos lo que dice la previa: series iguales en una fila, y los descansos
- [x] **TD-180** El NEXT dice cuanto dura y a que ritmo, y una duracion se escribe igual en todo el app
- [x] **TD-179** Los conteos de ejercicios y workouts, en singular o plural
- [x] **TD-177** El boton primario del app, con las seis decisiones de "Make any button look expensive"
- [x] **TD-173** La pantalla previa del training muestra lo que de verdad se va a hacer
- [x] **TD-168** Tres ajustes de pantalla: la hora de la alarma en JetBrains Mono, el historial de NIKO en History, y menos hueco bajo el wordmark
- [x] **TD-157** La alarma confirma lo que se toco: vibracion, el numero en grande y "Change"
- [x] **TD-150** Los trainings archivados tambien se ordenan arrastrando
- [x] **TD-109** La escala de dolor describe cada numero, no solo los extremos
- [x] **TD-107** El centro del player cede sitio a la nota: contador junto a las reps y tarjeta de peso translucida
- [x] **TD-074** Nombre del ejercicio en el player: dos lineas como mucho, sin cortar palabras y con alto fijo
