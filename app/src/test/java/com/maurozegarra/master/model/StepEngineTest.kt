package com.maurozegarra.master.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StepEngineTest {

    private fun ex(
        id: Long = 1,
        name: String = "Squat",
        prepareSec: Int = 10,
        sets: Int = 3,
        workMode: WorkMode = WorkMode.TIME,
        workValue: Int = 30,
        restSec: Int = 20,
        restSkipOnLastSet: Boolean = true,
        cooldownSec: Int = 15,
        weightType: WeightType = WeightType.NONE,
        barWeight: Double = 20.0,
        setList: List<WorkSet> = emptyList(),
    ) = Exercise(
        id = id,
        exerciseId = "squat",
        name = name,
        prepareSec = prepareSec,
        sets = sets,
        workMode = workMode,
        workValue = workValue,
        restSec = restSec,
        restSkipOnLastSet = restSkipOnLastSet,
        cooldownSec = cooldownSec,
        weightType = weightType,
        barWeight = barWeight,
        setList = setList,
    )

    private fun workout(
        id: Long = 1,
        name: String = "Lower",
        exercises: List<Exercise> = emptyList(),
        rotating: Boolean = false,
        rotationIndex: Int = 0,
        variants: List<WorkoutVariant> = emptyList(),
    ) = Workout(id = id, name = name, exercises = exercises, rotating = rotating, rotationIndex = rotationIndex, variants = variants)

    private fun training(
        id: Long = 1,
        name: String = "Hybrid",
        workouts: List<Workout> = emptyList(),
    ) = Training(id = id, name = name, workouts = workouts)

    @Test
    fun `single workout single exercise TIME produces prep work rest work rest work rest cooldown`() {
        val t = training(workouts = listOf(workout(exercises = listOf(ex(sets = 2, restSec = 15, restSkipOnLastSet = true)))))
        val steps = StepEngine.buildSteps(t)

        // prep, work(0), rest(0), work(1), [no rest on last set], cooldown
        assertEquals(5, steps.size)
        assertEquals(StepKind.PREP, steps[0].kind)
        assertEquals(StepKind.WORK, steps[1].kind)
        assertEquals(0, steps[1].setIndex)
        assertEquals(StepKind.REST, steps[2].kind)
        assertEquals(StepKind.WORK, steps[3].kind)
        assertEquals(1, steps[3].setIndex)
        assertEquals(StepKind.COOLDOWN, steps[4].kind)
    }

    @Test
    fun `rest is NOT skipped on last set when restSkipOnLastSet is false`() {
        val t = training(workouts = listOf(workout(exercises = listOf(ex(sets = 2, restSec = 15, restSkipOnLastSet = false)))))
        val steps = StepEngine.buildSteps(t)

        // prep, work(0), rest(0), work(1), rest(1), cooldown
        assertEquals(6, steps.size)
        assertEquals(StepKind.REST, steps[4].kind)
        assertEquals(1, steps[4].setIndex)
    }

    @Test
    fun `rest IS skipped on last set when restSkipOnLastSet is true`() {
        val t = training(workouts = listOf(workout(exercises = listOf(ex(sets = 2, restSec = 15, restSkipOnLastSet = true)))))
        val steps = StepEngine.buildSteps(t)

        // prep, work(0), rest(0), work(1), cooldown — no rest after last set
        assertEquals(5, steps.size)
        assertEquals(StepKind.COOLDOWN, steps[4].kind)
    }

    @Test
    fun `prepare is omitted when prepareSec is 0`() {
        val t = training(workouts = listOf(workout(exercises = listOf(ex(prepareSec = 0)))))
        val steps = StepEngine.buildSteps(t)

        assertEquals(StepKind.WORK, steps[0].kind)
    }

    @Test
    fun `cooldown is omitted when cooldownSec is 0`() {
        val t = training(workouts = listOf(workout(exercises = listOf(ex(cooldownSec = 0, restSec = 0)))))
        val steps = StepEngine.buildSteps(t)

        assertEquals(StepKind.WORK, steps.last().kind)
    }

    @Test
    fun `rest is omitted when restSec is 0`() {
        val t = training(workouts = listOf(workout(exercises = listOf(ex(sets = 3, restSec = 0, cooldownSec = 0)))))
        val steps = StepEngine.buildSteps(t)

        // prep, work, work, work — no rest at all
        assertEquals(4, steps.size)
        steps.filter { it.kind == StepKind.REST }.let { assertEquals(0, it.size) }
    }

    @Test
    fun `REPS mode produces manual steps with reps from setList`() {
        val e = ex(workMode = WorkMode.REPS, workValue = 12, sets = 2, restSec = 0, cooldownSec = 0, prepareSec = 0,
            weightType = WeightType.NONE, setList = listOf(WorkSet(reps = 10), WorkSet(reps = 8)))
        val t = training(workouts = listOf(workout(exercises = listOf(e))))
        val steps = StepEngine.buildSteps(t)

        assertEquals(2, steps.size)
        assertEquals(10, steps[0].reps)
        assertEquals(8, steps[1].reps)
        assertFalse(steps[0].timeBased)
        assertTrue(steps[0].manual)
    }

    @Test
    fun `REPS mode with weighted exercise carries weight info`() {
        val e = ex(workMode = WorkMode.REPS, sets = 1, restSec = 0, cooldownSec = 0, prepareSec = 0,
            weightType = WeightType.BARBELL, barWeight = 20.0, setList = listOf(WorkSet(reps = 5, weight = 40.0)))
        val t = training(workouts = listOf(workout(exercises = listOf(e))))
        val steps = StepEngine.buildSteps(t)

        assertEquals(1, steps.size)
        assertTrue(steps[0].weighted)
        assertEquals(60.0, steps[0].weightTotal, 0.001)
        assertEquals("20 + 40", steps[0].weightLabel)
    }

    @Test
    fun `multiple workouts produce correct workoutIndex and totalWorkouts`() {
        val t = training(workouts = listOf(
            workout(id = 1, name = "Warmup", exercises = listOf(ex(sets = 1, prepareSec = 0, restSec = 0, cooldownSec = 0))),
            workout(id = 2, name = "Main", exercises = listOf(ex(sets = 1, prepareSec = 0, restSec = 0, cooldownSec = 0))),
        ))
        val steps = StepEngine.buildSteps(t)

        assertEquals(2, steps.size)
        assertEquals(0, steps[0].workoutIndex)
        assertEquals(2, steps[0].totalWorkouts)
        assertEquals(1, steps[1].workoutIndex)
        assertEquals(2, steps[1].totalWorkouts)
        assertEquals("Warmup", steps[0].workoutName)
        assertEquals("Main", steps[1].workoutName)
    }

    @Test
    fun `rotating workout uses active variant exercises`() {
        val variant0 = WorkoutVariant(id = 10, name = "Running", exercises = listOf(ex(name = "Sprint", sets = 1, prepareSec = 0, restSec = 0, cooldownSec = 0)))
        val variant1 = WorkoutVariant(id = 11, name = "Cycling", exercises = listOf(ex(name = "Bike", sets = 1, prepareSec = 0, restSec = 0, cooldownSec = 0)))
        val w = workout(id = 1, name = "Cardio", rotating = true, rotationIndex = 0, variants = listOf(variant0, variant1))
        val t = training(workouts = listOf(w))
        val steps = StepEngine.buildSteps(t)

        assertEquals(1, steps.size)
        assertEquals("Sprint", steps[0].title)
        assertEquals("Running", steps[0].workoutName)
        assertEquals("Running", steps[0].variantName)
        assertTrue(steps[0].rotating)
    }

    @Test
    fun `rotating workout with rotationIndex 1 uses second variant`() {
        val variant0 = WorkoutVariant(id = 10, name = "Running", exercises = listOf(ex(name = "Sprint", sets = 1, prepareSec = 0, restSec = 0, cooldownSec = 0)))
        val variant1 = WorkoutVariant(id = 11, name = "Cycling", exercises = listOf(ex(name = "Bike", sets = 1, prepareSec = 0, restSec = 0, cooldownSec = 0)))
        val w = workout(id = 1, name = "Cardio", rotating = true, rotationIndex = 1, variants = listOf(variant0, variant1))
        val t = training(workouts = listOf(w))
        val steps = StepEngine.buildSteps(t)

        assertEquals("Bike", steps[0].title)
        assertEquals("Cycling", steps[0].workoutName)
    }

    @Test
    fun `empty training produces empty steps`() {
        val t = training(workouts = emptyList())
        val steps = StepEngine.buildSteps(t)
        assertTrue(steps.isEmpty())
    }

    @Test
    fun `workout with no exercises produces no steps for that workout`() {
        val t = training(workouts = listOf(
            workout(id = 1, exercises = emptyList()),
            workout(id = 2, exercises = listOf(ex(sets = 1, prepareSec = 0, restSec = 0, cooldownSec = 0))),
        ))
        val steps = StepEngine.buildSteps(t)

        assertEquals(1, steps.size)
        assertEquals(1, steps[0].workoutIndex)
    }

    @Test
    fun `single set exercise produces no rest when restSkipOnLastSet is true`() {
        val t = training(workouts = listOf(workout(exercises = listOf(ex(sets = 1, restSec = 30, restSkipOnLastSet = true, cooldownSec = 0)))))
        val steps = StepEngine.buildSteps(t)

        // prep, work — no rest (only set is last set), no cooldown
        assertEquals(2, steps.size)
        assertEquals(StepKind.PREP, steps[0].kind)
        assertEquals(StepKind.WORK, steps[1].kind)
    }

    @Test
    fun `DUMBBELL weight label shows 2x format`() {
        val e = ex(workMode = WorkMode.REPS, sets = 1, restSec = 0, cooldownSec = 0, prepareSec = 0,
            weightType = WeightType.DUMBBELL, setList = listOf(WorkSet(reps = 12, weight = 15.0)))
        val t = training(workouts = listOf(workout(exercises = listOf(e))))
        val steps = StepEngine.buildSteps(t)

        assertEquals("2 × 15", steps[0].weightLabel)
        assertEquals(30.0, steps[0].weightTotal, 0.001)
    }
}
