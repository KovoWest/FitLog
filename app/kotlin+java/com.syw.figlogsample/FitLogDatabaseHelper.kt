package com.syw.figlogsample


import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

// 매우 중요 !!! S07 화면에서 쓰이는 필요한 조회·비교·삭제 기능!
// SQL 관련 데이터 베이스 코틀린 파일임!!!

data class WorkoutDetail(
    val recordId: Long,
    val exerciseId: Long,
    val exerciseName: String,
    val bodyPart: String,
    val workoutDate: Long,
    val sets: List<WorkoutSet>
)

data class WorkoutSet(
    val setNumber: Int,
    val weight: Double,
    val reps: Int
)

class FitLogDatabaseHelper(
    private val contextForSchema: Context
) : SQLiteOpenHelper(
    contextForSchema,
    "fitlog.db",
    null,
    1
) {

    override fun onCreate(db: SQLiteDatabase) {
        FitLogDbHelper.initializeSchema(contextForSchema, db)
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int
    ) {
        // 기존 사용자 기록 보호:
        // 실제 스키마 변경 시 마이그레이션을 작성해야 합니다.
    }

    fun getWorkoutDetail(recordId: Long): WorkoutDetail? {

        val db = readableDatabase

        val cursor = db.rawQuery(
            """
            SELECT
                w.recordId,
                w.exerciseId,
                e.exerciseName,
                e.bodyPart,
                w.workoutDate
            FROM WorkoutRecord w
            JOIN Exercise e
              ON w.exerciseId = e.exerciseId
            WHERE w.recordId = ?
            """.trimIndent(),
            arrayOf(recordId.toString())
        )

        val detail = cursor.use {

            if (!it.moveToFirst()) {
                return null
            }

            WorkoutDetail(
                recordId = it.getLong(0),
                exerciseId = it.getLong(1),
                exerciseName = it.getString(2),
                bodyPart = it.getString(3),
                workoutDate = it.getLong(4),
                sets = getSets(recordId)
            )
        }

        return detail
    }

    fun getSets(recordId: Long): List<WorkoutSet> {

        val result = mutableListOf<WorkoutSet>()

        readableDatabase.rawQuery(
            """
            SELECT setNumber, weight, reps
            FROM SetRecord
            WHERE recordId = ?
            ORDER BY setNumber ASC
            """.trimIndent(),
            arrayOf(recordId.toString())
        ).use { cursor ->

            while (cursor.moveToNext()) {
                result.add(
                    WorkoutSet(
                        setNumber = cursor.getInt(0),
                        weight = cursor.getDouble(1),
                        reps = cursor.getInt(2)
                    )
                )
            }
        }

        return result
    }

    fun getPreviousRecords(
        exerciseId: Long,
        currentDate: Long,
        currentRecordId: Long
    ): List<WorkoutDetail> {

        val ids = mutableListOf<Long>()

        readableDatabase.rawQuery(
            """
            SELECT recordId
            FROM WorkoutRecord
            WHERE exerciseId = ?
              AND (
                  workoutDate < ?
                  OR (
                      workoutDate = ?
                      AND recordId < ?
                  )
              )
            ORDER BY workoutDate DESC, recordId DESC
            """.trimIndent(),
            arrayOf(
                exerciseId.toString(),
                currentDate.toString(),
                currentDate.toString(),
                currentRecordId.toString()
            )
        ).use { cursor ->

            while (cursor.moveToNext()) {
                ids.add(cursor.getLong(0))
            }
        }

        return ids.mapNotNull { getWorkoutDetail(it) }
    }

    fun deleteWorkout(recordId: Long): Boolean {

        val db = writableDatabase

        db.beginTransaction()

        return try {

            db.delete(
                "SetRecord",
                "recordId = ?",
                arrayOf(recordId.toString())
            )

            val deleted = db.delete(
                "WorkoutRecord",
                "recordId = ?",
                arrayOf(recordId.toString())
            )

            db.setTransactionSuccessful()

            deleted > 0

        } finally {
            db.endTransaction()
        }
    }
}