package com.rutina.app.ui.tutorial

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

/** Pantalla de ejemplo que se dibuja dentro del teléfono de la guía. */
enum class Mock {
    HOME, HOME_FAB, HABITS, HABITS_DIALOG, HABIT_WEEK, HABIT_FORM, HABIT_GOAL, HABIT_REMINDERS, SUGGESTIONS,
    HABIT_STATS, STATS_OVERVIEW, LIMIT, QUIT, MOOD, WEIGHT, HEALTH, JOURNAL_HABIT, DRAWER,
    TASKS, TASK_SHEET, TASK_FORM, LISTS,
    JOURNAL, NOTE,
    TIMER, COUNTDOWN,
    CALENDAR, CAL_MONTH, CAL_SCHEDULE,
    SETTINGS, BACKUP, ACHIEVEMENTS, ARCHIVE,
    WIDGET_HABITS, WIDGET_TASKS,
}

/**
 * Una guía: título, pantalla de ejemplo, dónde "tocar" (fracción 0..1 del teléfono)
 * y tres pasos.
 */
data class Guide(val title: String, val mock: Mock, val tap: Offset?, val steps: List<String>)

data class GuideSection(val name: String, val color: Color, val panel: Color, val guides: List<Guide>)

/** Una guía dentro de la lista completa (para el carrusel). */
data class GuidePage(val section: GuideSection, val index: Int, val guide: Guide)

private fun g(title: String, mock: Mock, tap: Offset?, vararg steps: String) = Guide(title, mock, tap, steps.toList())
private fun at(x: Float, y: Float) = Offset(x, y)

object Tutorial {

    val sections: List<GuideSection> = listOf(
        GuideSection(
            "General", Color(0xFFB0BEC5), Color(0xFF353B40),
            listOf(
                g(
                    "Bienvenido a Rutina", Mock.HOME, at(0.86f, 0.9f),
                    "Abajo tienes cuatro pestañas: Hoy, Diario, Calendario y Ajustes",
                    "En \"Hoy\" ves las tareas y los hábitos del día que elijas en la semana",
                    "Toca + para crear una tarea o un hábito",
                ),
                g(
                    "Buscar tareas y hábitos", Mock.SETTINGS, at(0.5f, 0.36f),
                    "Abre \"Buscar\" desde \"Ajustes\" o desde el menú ☰ de \"Hoy\"",
                    "Escribe parte del nombre o la descripción de lo que buscas",
                    "Toca un resultado para completarlo, registrarlo o editarlo. Para tus notas usa la lupa del \"Diario\"",
                ),
                g(
                    "Logros de toda la vida", Mock.ACHIEVEMENTS, null,
                    "Abre \"Ajustes\" y elige \"Logros\"",
                    "Revisa totales de actividad, récords de escritura y tus mejores rachas",
                    "Toca un total para ver qué contribuye y cuáles son los próximos hitos",
                ),
                g(
                    "Ajustes y apariencia", Mock.SETTINGS, at(0.5f, 0.78f),
                    "Abre \"Ajustes\" para ver las opciones de la app",
                    "Cambia el tamaño del texto y el día con el que empieza la semana",
                    "Elige con qué pestaña abre la app y las vistas predeterminadas de hábitos y calendario",
                ),
                g(
                    "Crear un respaldo", Mock.BACKUP, at(0.5f, 0.82f),
                    "En \"Ajustes\" toca \"Crear respaldo\"",
                    "Si quieres, protégelo con contraseña (cifrado AES‑256)",
                    "Elige Google Drive o una carpeta del teléfono para guardar el archivo",
                ),
                g(
                    "Cambiar de teléfono", Mock.SETTINGS, null,
                    "Instala Rutina en el teléfono nuevo",
                    "En \"Ajustes\" toca \"Restaurar respaldo\" y elige tu archivo (de Drive o de una carpeta)",
                    "Confirma: lo que haya en ese teléfono se reemplaza por tu respaldo",
                ),
                g(
                    "Eliminar datos", Mock.SETTINGS, null,
                    "En \"Ajustes\" toca \"Eliminar datos\"",
                    "Elige qué borrar: hábitos, tareas, notas o sesiones del temporizador",
                    "Confirma. No se puede deshacer, así que crea un respaldo antes",
                ),
                g(
                    "Tu privacidad", Mock.SETTINGS, at(0.5f, 0.2f),
                    "Rutina no tiene permiso de Internet: Android no la deja enviar nada",
                    "No hay cuentas, anuncios ni nube; todo vive solo en este teléfono",
                    "El respaldo es el único archivo que sale, y solo cuando tú lo creas",
                ),
            ),
        ),
        GuideSection(
            "Hábitos", Color(0xFFF28B95), Color(0xFF4A2A30),
            listOf(
                g(
                    "Crear un hábito", Mock.HOME_FAB, at(0.7f, 0.79f),
                    "Abre \"Hoy\", toca el botón + y elige \"Hábito\"",
                    "Elige una sugerencia o toca \"Hábito personalizado\"",
                    "Define nombre, tipo, objetivo, color y recordatorios opcionales, y guarda",
                ),
                g(
                    "Sugerencias", Mock.SUGGESTIONS, at(0.5f, 0.3f),
                    "Las sugerencias vienen agrupadas: Fitness, Salud, Mente, Hogar, Reducir, Dejar…",
                    "Toca una para abrir el formulario ya lleno",
                    "Cambia lo que quieras antes de guardar",
                ),
                g(
                    "Tipos de seguimiento", Mock.HABIT_FORM, at(0.5f, 0.32f),
                    "\"Recuento\" suma veces, minutos, vasos, páginas…",
                    "\"Dejar\", \"Estado de ánimo\", \"Peso\", \"Salud\" y \"Diario\" tienen su propio registro",
                    "Puedes cambiar el tipo después, pero su historial empieza de cero",
                ),
                g(
                    "Objetivos y unidades", Mock.HABIT_GOAL, at(0.5f, 0.42f),
                    "Elige 👍 Positivo (llegar a una meta), 👎 Negativo (no pasarte) o Sin objetivo",
                    "Define la cantidad y la unidad: vez, min, vasos, páginas o la tuya",
                    "Elige si la meta es por día, por semana o por mes",
                ),
                g(
                    "Días del hábito", Mock.HABIT_GOAL, at(0.5f, 0.6f),
                    "Elige todos los días, días específicos o cierto número de días por semana",
                    "Los días que no toca, el hábito no aparece en \"Hoy\" ni rompe tu racha",
                    "El día con el que empieza la semana se cambia en \"Ajustes\"",
                ),
                g(
                    "Registrar actividad", Mock.HABITS, at(0.9f, 0.27f),
                    "Toca el botón de la derecha del hábito para sumar 1",
                    "Toca la fila para escribir una cantidad exacta o añadir una nota",
                    "En esa misma ventana puedes deshacer el último registro",
                ),
                g(
                    "Cantidad exacta", Mock.HABITS_DIALOG, at(0.5f, 0.52f),
                    "Toca la fila del hábito",
                    "Escribe la cantidad (por ejemplo 15 min) y una nota opcional",
                    "Toca \"Añadir\". La nota aparece también en tu Diario",
                ),
                g(
                    "Días anteriores", Mock.HABITS, at(0.42f, 0.15f),
                    "Toca un día en la franja de la semana",
                    "Registra o deshaz en ese día como si fuera hoy",
                    "Usa ‹ › para cambiar de semana y toca el título para volver a hoy",
                ),
                g(
                    "Vistas semanal, mensual y anual", Mock.HABIT_WEEK, at(0.6f, 0.3f),
                    "Abre el menú ☰ de \"Hoy\" y busca \"Visualización de hábitos\"",
                    "Elige Compacto, Semanal, Mensual o Anual",
                    "Toca un día para sumar y mantenlo pulsado para deshacer",
                ),
                g(
                    "Estadísticas de un hábito", Mock.HABIT_STATS, null,
                    "Toca el nombre de color de un hábito",
                    "Revisa racha actual, mejor racha, % de éxito y totales",
                    "Cambia de mes para ver el calendario, la gráfica y el mapa del año",
                ),
                g(
                    "Resumen general", Mock.STATS_OVERVIEW, null,
                    "Toca el ícono de gráfica arriba a la izquierda en \"Hoy\"",
                    "Mira cuántos hábitos cumpliste y cuántas tareas completaste",
                    "Toca un hábito de la lista para ver su detalle",
                ),
                g(
                    "Reducir hábitos", Mock.LIMIT, at(0.9f, 0.27f),
                    "Elige una sugerencia de \"Reducir\" o el objetivo 👎 Negativo",
                    "Define la cantidad máxima permitida en cada periodo",
                    "Registra sin pasarte del límite para mantener la racha",
                ),
                g(
                    "Dejar hábitos", Mock.QUIT, at(0.5f, 0.27f),
                    "Elige una sugerencia de \"Dejar\" o el tipo de seguimiento \"Dejar\"",
                    "Indica la fecha y hora en que lo dejaste",
                    "Si recaes, toca la fila y \"Reiniciar contador\": queda guardado y ves tu mejor marca",
                ),
                g(
                    "Estado de ánimo", Mock.MOOD, at(0.9f, 0.27f),
                    "Crea un hábito con el tipo de seguimiento \"Estado de ánimo\"",
                    "Registra cómo te sientes y elige las emociones que influyen",
                    "En sus estadísticas ves tu promedio, emociones frecuentes y tendencia",
                ),
                g(
                    "Peso corporal", Mock.WEIGHT, at(0.9f, 0.27f),
                    "Crea un hábito de \"Peso corporal\" con unidad y objetivo opcional",
                    "Registra tu peso de hoy o de otra fecha",
                    "Revisa la gráfica, los cambios y el progreso hacia tu objetivo",
                ),
                g(
                    "Sincronización de salud", Mock.HEALTH, at(0.9f, 0.27f),
                    "Crea un hábito de \"Salud\" y elige pasos, distancia, calorías, pisos o ejercicio",
                    "Cuando se te pida, permite que Rutina lea esos datos de Health Connect",
                    "Se actualiza al abrir la app; toca ↻ en el hábito cuando quieras",
                ),
                g(
                    "Objetivos del diario", Mock.JOURNAL_HABIT, at(0.9f, 0.27f),
                    "Elige el tipo de seguimiento \"Diario\" y una meta de palabras",
                    "Escribe en \"Diario\" y etiqueta la nota con ese hábito",
                    "El recuento de palabras actualiza tu progreso",
                ),
                g(
                    "Recordatorios", Mock.HABIT_REMINDERS, at(0.5f, 0.62f),
                    "Edita un hábito y baja a \"Recordatorios\"",
                    "Añade una o varias horas y elige los días",
                    "Si ya cumpliste ese día, el aviso no suena. En Recuento puedes sumar desde la notificación",
                ),
                g(
                    "Archivar hábitos", Mock.ARCHIVE, at(0.62f, 0.2f),
                    "Edita el hábito y toca \"Archivar\" para ocultarlo sin perder su historial",
                    "Abre \"Archivo de hábitos\" desde \"Ajustes\"",
                    "Restáuralo o elimínalo definitivamente",
                ),
            ),
        ),
        GuideSection(
            "Tareas", Color(0xFF9FA8DA), Color(0xFF2E3350),
            listOf(
                g(
                    "Crear una tarea", Mock.TASK_SHEET, at(0.5f, 0.62f),
                    "En \"Hoy\" toca + y elige \"Tarea\"",
                    "Escribe qué quieres hacer y, si quieres, una descripción",
                    "Elige fecha, prioridad y lista, y guárdala",
                ),
                g(
                    "Fecha, hora y duración", Mock.TASK_FORM, at(0.5f, 0.3f),
                    "Toca \"Más opciones\" en la tarea rápida o abre una tarea existente",
                    "Elige fecha, hora y cuánto dura",
                    "Las tareas con hora aparecen en la cuadrícula del \"Calendario\"",
                ),
                g(
                    "Repetir tareas", Mock.TASK_FORM, at(0.5f, 0.47f),
                    "En el editor elige \"Repetir\": cada día, entre semana, cada semana, mes o año",
                    "Al completarla se crea sola la siguiente",
                    "Si la desmarcas por error, la repetición creada se quita",
                ),
                g(
                    "Prioridades", Mock.TASK_FORM, at(0.5f, 0.56f),
                    "Toca la bandera para elegir prioridad baja, media o alta",
                    "Las más importantes aparecen primero",
                    "Usa \"Prioridad mínima\" en un filtro para ver solo las urgentes",
                ),
                g(
                    "Avisos de tareas", Mock.TASK_FORM, at(0.85f, 0.73f),
                    "Pon fecha y hora a la tarea",
                    "Activa el aviso y permite las notificaciones",
                    "Desde la notificación puedes marcarla como completada",
                ),
                g(
                    "Completar tareas", Mock.HOME, at(0.9f, 0.24f),
                    "Toca la casilla de la derecha para completarla",
                    "Tócala otra vez si te equivocaste",
                    "Las tareas completadas quedan registradas en tu \"Diario\"",
                ),
                g(
                    "Listas", Mock.LISTS, at(0.5f, 0.25f),
                    "Abre ☰ y toca \"Gestionar listas / filtros\"",
                    "Crea listas con nombre y color",
                    "Elige la lista al crear una tarea y ábrela desde el menú ☰",
                ),
                g(
                    "Filtros", Mock.LISTS, at(0.5f, 0.6f),
                    "En \"Gestionar listas / filtros\" crea un filtro",
                    "Combina fecha, prioridad mínima, lista y si incluye completadas",
                    "Ábrelo desde el menú ☰ para ver solo esas tareas",
                ),
                g(
                    "Todas las tareas", Mock.TASKS, null,
                    "Abre ☰ y elige \"Todas las tareas\"",
                    "Se agrupan en Vencidas, Hoy, Mañana, Próximas, Sin fecha y Completadas",
                    "Toca una tarea para editarla o eliminarla",
                ),
                g(
                    "El menú ☰", Mock.DRAWER, null,
                    "Cambia entre \"Vista de hoy\", todas las tareas, todos los hábitos, tus listas y filtros",
                    "Elige cómo ver los hábitos: compacto, semanal, mensual o anual",
                    "Abajo están \"Buscar\" y \"Gestionar listas / filtros\"",
                ),
            ),
        ),
        GuideSection(
            "Diario", Color(0xFFA5D6A7), Color(0xFF2A4030),
            listOf(
                g(
                    "Escribir una nota", Mock.JOURNAL, at(0.86f, 0.9f),
                    "Abre \"Diario\" y toca +",
                    "Escribe lo que quieras; se guarda solo al salir",
                    "Toca la fecha para cambiar el día y la hora de la nota",
                ),
                g(
                    "Dar formato", Mock.NOTE, at(0.3f, 0.2f),
                    "Usa la barra para negrita, cursiva, tachado, resaltado y títulos",
                    "Crea listas y casillas de verificación",
                    "En el \"Diario\" puedes marcar las casillas sin abrir la nota",
                ),
                g(
                    "Etiquetar con un hábito", Mock.NOTE, at(0.3f, 0.11f),
                    "Toca \"Sin etiqueta\" y elige un hábito",
                    "En hábitos de tipo \"Diario\" las palabras cuentan para la meta",
                    "Desde el hábito, \"Escribir nota\" ya la crea etiquetada",
                ),
                g(
                    "Notas marcadas", Mock.JOURNAL, at(0.1f, 0.06f),
                    "En el editor toca el marcador para destacar una nota",
                    "En \"Diario\" toca el marcador de arriba a la izquierda",
                    "Verás solo tus notas marcadas",
                ),
                g(
                    "Filtrar y buscar", Mock.JOURNAL, at(0.3f, 0.14f),
                    "Elige ver todas las entradas, solo notas o actividad de tareas o hábitos",
                    "Filtra por etiqueta de hábito",
                    "Toca la lupa para buscar cualquier texto",
                ),
                g(
                    "Historial de actividad", Mock.JOURNAL, at(0.5f, 0.45f),
                    "Cada registro de hábito y tarea completada aparece en el \"Diario\"",
                    "Toca un registro para editar su nota o eliminarlo",
                    "Si lo eliminas, también se quita del progreso del hábito",
                ),
                g(
                    "Exportar notas", Mock.JOURNAL, at(0.9f, 0.06f),
                    "Toca ⋯ en el \"Diario\" y \"Exportar notas\"",
                    "Se crea un archivo Markdown con lo que estás viendo",
                    "Guárdalo en Drive o en una carpeta del teléfono",
                ),
            ),
        ),
        GuideSection(
            "Temporizador", Color(0xFFF6C177), Color(0xFF4A3A25),
            listOf(
                g(
                    "Iniciar un temporizador", Mock.HOME, at(0.9f, 0.06f),
                    "Toca el reloj arriba a la derecha en \"Hoy\"",
                    "Elige el hábito de Recuento al que sumarás el tiempo (en min u horas)",
                    "Elige \"Cronómetro\" o \"Cuenta atrás\" e inicia la sesión",
                ),
                g(
                    "Cronómetro", Mock.TIMER, at(0.7f, 0.42f),
                    "Elige \"Cronómetro\" y un hábito",
                    "Usa Iniciar, Pausa, Reanudar y Vuelta durante la sesión",
                    "Revisa \"Vueltas\" o \"Eventos\" y detén la sesión para guardar el tiempo",
                ),
                g(
                    "Cuenta atrás", Mock.COUNTDOWN, at(0.5f, 0.75f),
                    "Elige \"Cuenta atrás\", un hábito y la duración con la rueda o un atajo",
                    "Inicia y permite las notificaciones cuando se te pida",
                    "Al terminar suena aunque la app esté cerrada y te pregunta si registrar",
                ),
                g(
                    "En segundo plano", Mock.TIMER, null,
                    "El temporizador sigue aunque salgas de la app o apagues la pantalla",
                    "Pausa, reanuda o detén desde la notificación",
                    "Mientras corre, el reloj de \"Hoy\" se pinta de color",
                ),
                g(
                    "Sesiones guardadas", Mock.CALENDAR, at(0.6f, 0.5f),
                    "Cada sesión guardada aparece en el \"Calendario\" con ⏱",
                    "El tiempo se suma al hábito elegido",
                    "Revisa tus horas de concentración en \"Logros\"",
                ),
            ),
        ),
        GuideSection(
            "Calendario", Color(0xFFB39DDB), Color(0xFF3A3048),
            listOf(
                g(
                    "Vistas del calendario", Mock.CALENDAR, at(0.62f, 0.06f),
                    "Toca el selector de arriba y elige \"Día\", \"3 días\", \"Semana\" o \"Mes\"",
                    "Usa las flechas para cambiar de periodo y \"Hoy\" para volver",
                    "Toca el mes para saltar a cualquier fecha",
                ),
                g(
                    "Planificar tareas", Mock.CALENDAR, at(0.4f, 0.7f),
                    "Toca un hueco de la cuadrícula para crear una tarea a esa hora",
                    "O toca + para crearla a la siguiente hora",
                    "Las tareas sin hora van en la fila \"Todo el día\"",
                ),
                g(
                    "Mover tareas", Mock.CALENDAR, at(0.5f, 0.38f),
                    "Mantén pulsada una tarea con hora",
                    "Arrástrala a otra hora o a otro día",
                    "Suéltala para guardar el cambio",
                ),
                g(
                    "Programar tareas", Mock.CAL_SCHEDULE, at(0.8f, 0.6f),
                    "Toca ⋯ y elige \"Programar tareas\"",
                    "Verás las tareas que aún no tienen hora",
                    "Elige día y hora para cada una",
                ),
                g(
                    "Vista mensual", Mock.CAL_MONTH, at(0.45f, 0.45f),
                    "Elige \"Mes\" en el selector",
                    "Cada día muestra sus tareas; las repeticiones futuras se ven atenuadas",
                    "Toca un día para abrirlo en la vista de día",
                ),
            ),
        ),
        GuideSection(
            "Widgets", Color(0xFF8FD3C1), Color(0xFF24403C),
            listOf(
                g(
                    "Añadir un widget", Mock.WIDGET_TASKS, null,
                    "En \"Ajustes\" → \"Widgets\" toca \"Añadir\"",
                    "O mantén pulsada la pantalla de inicio → Widgets → Rutina",
                    "Mantén pulsado el widget para cambiar su tamaño",
                ),
                g(
                    "Hábitos de hoy", Mock.WIDGET_HABITS, at(0.86f, 0.13f),
                    "Muestra los hábitos que tocan hoy con su progreso",
                    "Toca + para sumar 1 sin abrir la app",
                    "Toca el título o los demás botones para abrir Rutina",
                ),
                g(
                    "Lista de tareas", Mock.WIDGET_TASKS, at(0.86f, 0.13f),
                    "Muestra las tareas de hoy y las vencidas (o las próximas si no hay)",
                    "Toca ✓ para completar una tarea",
                    "Toca una tarea para continuar en Rutina",
                ),
            ),
        ),
    )

    val pages: List<GuidePage> = sections.flatMap { s -> s.guides.mapIndexed { i, g -> GuidePage(s, i, g) } }

    fun firstPageOf(section: GuideSection): Int = pages.indexOfFirst { it.section == section }
}
