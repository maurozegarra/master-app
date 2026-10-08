package com.maurozegarra.master.morning

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.MonthDay

/**
 * Los feriados (7-oct-2026). Un feriado se conoce de antemano -casi todos son fijos-, así
 * que no tiene sentido saltarlo a mano la víspera con "Skip tomorrow", que además deja el
 * día sin ninguna alarma. Un feriado suena como un sábado ([AS]): ni la de las 5:00 del día
 * presencial ni silencio, la de los días en casa.
 *
 * Vienen cargados los feriados nacionales del Perú, y el usuario quita los que no le dan
 * libre y agrega los suyos: los días no laborables que decreta el gobierno, o los de su
 * trabajo.
 */
object Holidays {

    /** El día de la semana con el que suena un feriado. */
    val AS: DayOfWeek = DayOfWeek.SATURDAY

    private val FIJOS: Map<MonthDay, String> = linkedMapOf(
        MonthDay.of(1, 1) to "New Year's Day",
        MonthDay.of(5, 1) to "Labour Day",
        MonthDay.of(6, 7) to "Battle of Arica and Flag Day",
        MonthDay.of(6, 29) to "St. Peter and St. Paul",
        MonthDay.of(7, 23) to "Peruvian Air Force Day",
        MonthDay.of(7, 28) to "Independence Day",
        MonthDay.of(7, 29) to "Independence Day",
        MonthDay.of(8, 6) to "Battle of Junín",
        MonthDay.of(8, 30) to "Santa Rosa de Lima",
        MonthDay.of(10, 8) to "Battle of Angamos",
        MonthDay.of(11, 1) to "All Saints' Day",
        MonthDay.of(12, 8) to "Immaculate Conception",
        MonthDay.of(12, 9) to "Battle of Ayacucho",
        MonthDay.of(12, 25) to "Christmas Day",
    )

    /** Los feriados nacionales del Perú de [year], con su nombre. */
    fun peru(year: Int): Map<LocalDate, String> {
        val pascua = easter(year)
        val moviles = mapOf(
            pascua.minusDays(3) to "Holy Thursday",
            pascua.minusDays(2) to "Good Friday",
        )
        return (FIJOS.mapKeys { it.key.atYear(year) } + moviles).toSortedMap()
    }

    /**
     * Los feriados vigentes: los del Perú de este año y el siguiente, menos los que quitó,
     * más los que agregó. Dos años para que en diciembre ya se vea el 1 de enero.
     */
    fun of(year: Int, added: Set<LocalDate>, removed: Set<LocalDate>): Set<LocalDate> =
        ((peru(year).keys + peru(year + 1).keys) - removed) + added

    /** El nombre de [day]: el del feriado nacional, o null si es uno agregado por él. */
    fun name(day: LocalDate): String? = peru(day.year)[day]

    /**
     * El domingo de Pascua de [year], calendario gregoriano (algoritmo anónimo, el de Meeus).
     * De él salen el Jueves y el Viernes Santo, los únicos feriados que se mueven.
     */
    fun easter(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = (h + l - 7 * m + 114) % 31 + 1
        return LocalDate.of(year, month, day)
    }
}
