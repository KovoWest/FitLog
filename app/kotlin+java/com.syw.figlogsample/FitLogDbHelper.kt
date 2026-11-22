package com.syw.figlogsample

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * FitLog 데이터베이스 관리 클래스
 *
 * 주요 기능
 * 1. 운동 종목 관리
 * 2. 운동 루틴 관리
 * 3. 운동 루틴과 운동 종목 연결
 * 4. 운동 기록 테이블 관리
 * 5. 운동 일정 테이블 관리
 * 6. 운동 루틴 생성
 * 7. 운동 루틴 수정
 * 8. 운동 루틴 삭제
 *
 * S02/S03 공통 사용
 */

// 운동 루틴 데이터 모델
data class FitRoutine(
    val id: Long,
    val name: String,
    val bodyPart: String,
    val exercises: List<String>
)

class FitLogDbHelper(
    context: Context
) : SQLiteOpenHelper(
    context,
    "fitlog.db",
    null,
    1
) {

    /**
     * 데이터베이스 최초 생성
     */
    companion object {
        fun initializeSchema(context: Context, db: SQLiteDatabase) {
            FitLogDbHelper(context).onCreate(db)
        }
    }

    override fun onCreate(db: SQLiteDatabase) {

        // 운동 종목 테이블
        db.execSQL(
            """
            CREATE TABLE Exercise (
                exerciseId INTEGER PRIMARY KEY AUTOINCREMENT,
                exerciseName TEXT NOT NULL UNIQUE,
                bodyPart TEXT NOT NULL,
                description TEXT
            )
            """.trimIndent()
        )

        // 운동 루틴 테이블
        db.execSQL(
            """
            CREATE TABLE Routine (
                routineId INTEGER PRIMARY KEY AUTOINCREMENT,
                routineName TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                bodyPart TEXT NOT NULL DEFAULT '기타'
            )
            """.trimIndent()
        )

        // 루틴별 운동 종목 연결 테이블
        db.execSQL(
            """
            CREATE TABLE RoutineExercise (
                routineExerciseId INTEGER PRIMARY KEY AUTOINCREMENT,
                routineId INTEGER NOT NULL,
                exerciseId INTEGER NOT NULL,
                exerciseOrder INTEGER NOT NULL
                    CHECK(exerciseOrder >= 1),

                FOREIGN KEY(routineId)
                    REFERENCES Routine(routineId)
                    ON DELETE CASCADE,

                FOREIGN KEY(exerciseId)
                    REFERENCES Exercise(exerciseId)
            )
            """.trimIndent()
        )

        // 운동 순서 조회용 인덱스
        db.execSQL(
            """
            CREATE INDEX idx_re_routine
            ON RoutineExercise(routineId, exerciseOrder)
            """.trimIndent()
        )

        // 운동 기록 테이블
        db.execSQL(
            """
            CREATE TABLE WorkoutRecord (
                recordId INTEGER PRIMARY KEY AUTOINCREMENT,
                exerciseId INTEGER NOT NULL,
                routineId INTEGER,
                workoutDate INTEGER NOT NULL
            )
            """.trimIndent()
        )

        // 세트 기록 테이블
        db.execSQL(
            """
            CREATE TABLE SetRecord (
                setRecordId INTEGER PRIMARY KEY AUTOINCREMENT,
                recordId INTEGER NOT NULL,
                setNumber INTEGER NOT NULL,
                weight REAL NOT NULL CHECK(weight >= 0),
                reps INTEGER NOT NULL CHECK(reps >= 1)
            )
            """.trimIndent()
        )

        // 운동 일정 테이블
        db.execSQL(
            """
            CREATE TABLE Schedule (
                scheduleId INTEGER PRIMARY KEY AUTOINCREMENT,
                routineId INTEGER,
                scheduleDate INTEGER NOT NULL,
                completionStatus INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )

        // 기본 운동 루틴 등록
        seed(db)
    }

    /**
     * 외래키 사용 설정
     */
    override fun onConfigure(db: SQLiteDatabase) {

        super.onConfigure(db)

        db.setForeignKeyConstraintsEnabled(true)
    }

    /**
     * 데이터베이스 버전 변경
     */
    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int
    ) {

        // 추후 DB 버전 변경 시
        // 데이터 보존용 마이그레이션 구현
    }

    /**
     * 최초 설치 시 기본 운동 루틴 생성
     */
    private fun seed(db: SQLiteDatabase) {

        insertRoutine(
            db,
            "가슴 운동 루틴",
            "가슴",
            listOf(
                "벤치프레스",
                "인클라인 벤치프레스",
                "체스트 프레스",
                "딥스"
            )
        )

        insertRoutine(
            db,
            "하체 운동 루틴",
            "하체",
            listOf(
                "스쿼트",
                "레그프레스",
                "레그익스텐션",
                "레그컬",
                "카프레이즈"
            )
        )

        insertRoutine(
            db,
            "등 운동 루틴",
            "등",
            listOf(
                "랫풀다운",
                "바벨로우",
                "시티드로우",
                "풀업"
            )
        )

        insertRoutine(
            db,
            "어깨 운동 루틴",
            "어깨",
            listOf(
                "오버헤드프레스",
                "사이드 레터럴 레이즈",
                "프론트 레이즈",
                "리어 델트 플라이"
            )
        )
    }

    /**
     * 기본 운동 루틴 생성
     */
    private fun insertRoutine(
        db: SQLiteDatabase,
        name: String,
        part: String,
        exercises: List<String>
    ) {

        val routineId = db.insertOrThrow(
            "Routine",
            null,
            ContentValues().apply {

                put("routineName", name)
                put("bodyPart", part)

                put(
                    "createdAt",
                    System.currentTimeMillis()
                )
            }
        )

        exercises.forEachIndexed { index, exerciseName ->

            // 운동 종목 추가
            db.insertWithOnConflict(
                "Exercise",
                null,
                ContentValues().apply {

                    put("exerciseName", exerciseName)
                    put("bodyPart", part)
                },
                SQLiteDatabase.CONFLICT_IGNORE
            )

            // 운동 ID 조회
            val exerciseId = db.rawQuery(
                """
                SELECT exerciseId
                FROM Exercise
                WHERE exerciseName=?
                """.trimIndent(),
                arrayOf(exerciseName)
            ).use { cursor ->

                check(cursor.moveToFirst()) {
                    "운동 종목을 찾지 못했습니다: $exerciseName"
                }

                cursor.getLong(0)
            }

            // 루틴과 운동 종목 연결
            db.insertOrThrow(
                "RoutineExercise",
                null,
                ContentValues().apply {

                    put("routineId", routineId)
                    put("exerciseId", exerciseId)
                    put("exerciseOrder", index + 1)
                }
            )
        }
    }

    /**
     * 저장된 운동 루틴 목록 조회
     */
    fun listRoutines(): List<FitRoutine> {

        val result = mutableListOf<FitRoutine>()

        val db = readableDatabase

        db.rawQuery(
            """
            SELECT routineId, routineName, bodyPart
            FROM Routine
            ORDER BY createdAt, routineId
            """.trimIndent(),
            null
        ).use { cursor ->

            while (cursor.moveToNext()) {

                val id = cursor.getLong(0)

                val names = mutableListOf<String>()

                db.rawQuery(
                    """
                    SELECT e.exerciseName
                    FROM RoutineExercise re
                    JOIN Exercise e
                        ON e.exerciseId = re.exerciseId
                    WHERE re.routineId = ?
                    ORDER BY re.exerciseOrder
                    """.trimIndent(),
                    arrayOf(id.toString())
                ).use { exerciseCursor ->

                    while (exerciseCursor.moveToNext()) {

                        names.add(
                            exerciseCursor.getString(0)
                        )
                    }
                }

                result.add(
                    FitRoutine(
                        id,
                        cursor.getString(1),
                        cursor.getString(2),
                        names
                    )
                )
            }
        }

        return result
    }

    /**
     * 운동 루틴 생성 및 수정
     */
    fun saveRoutine(
        id: Long?,
        name: String,
        part: String,
        exercises: List<String>
    ): Long {

        require(name.isNotBlank()) {
            "루틴 이름을 입력해 주세요."
        }

        require(exercises.isNotEmpty()) {
            "운동 종목을 한 개 이상 선택해 주세요."
        }

        require(exercises.none { it.isBlank() }) {
            "빈 운동 종목은 사용할 수 없습니다."
        }

        val db = writableDatabase

        db.beginTransaction()

        try {

            val routineId = if (id == null) {

                // 새로운 루틴 생성
                db.insertOrThrow(
                    "Routine",
                    null,
                    ContentValues().apply {

                        put("routineName", name.trim())
                        put("bodyPart", part)

                        put(
                            "createdAt",
                            System.currentTimeMillis()
                        )
                    }
                )

            } else {

                // 기존 루틴 수정
                val changed = db.update(
                    "Routine",
                    ContentValues().apply {

                        put("routineName", name.trim())
                        put("bodyPart", part)
                    },
                    "routineId=?",
                    arrayOf(id.toString())
                )

                require(changed == 1) {
                    "수정할 루틴을 찾을 수 없습니다."
                }

                // 기존 루틴 운동 연결 제거
                db.delete(
                    "RoutineExercise",
                    "routineId=?",
                    arrayOf(id.toString())
                )

                id
            }

            // 운동 종목 저장
            exercises.forEachIndexed { index, exerciseName ->

                val trimmed = exerciseName.trim()

                db.insertWithOnConflict(
                    "Exercise",
                    null,
                    ContentValues().apply {

                        put("exerciseName", trimmed)
                        put("bodyPart", part)
                    },
                    SQLiteDatabase.CONFLICT_IGNORE
                )

                var exerciseId = -1L

                db.rawQuery(
                    """
                    SELECT exerciseId
                    FROM Exercise
                    WHERE exerciseName=?
                    """.trimIndent(),
                    arrayOf(trimmed)
                ).use { cursor ->

                    if (cursor.moveToFirst()) {

                        exerciseId = cursor.getLong(0)
                    }
                }

                check(exerciseId != -1L)

                // 루틴과 운동 종목 연결
                db.insertOrThrow(
                    "RoutineExercise",
                    null,
                    ContentValues().apply {

                        put("routineId", routineId)
                        put("exerciseId", exerciseId)
                        put("exerciseOrder", index + 1)
                    }
                )
            }

            db.setTransactionSuccessful()

            return routineId

        } finally {

            db.endTransaction()
        }
    }

    /**
     * 운동 루틴 삭제
     */
    fun deleteRoutine(id: Long) {

        val db = writableDatabase

        db.beginTransaction()

        try {

            // 기존 운동 기록은 보존
            db.execSQL(
                """
                UPDATE Schedule
                SET routineId=NULL
                WHERE routineId=?
                """.trimIndent(),
                arrayOf(id)
            )

            db.execSQL(
                """
                UPDATE WorkoutRecord
                SET routineId=NULL
                WHERE routineId=?
                """.trimIndent(),
                arrayOf(id)
            )

            // 루틴과 운동 종목 연결 삭제
            db.delete(
                "RoutineExercise",
                "routineId=?",
                arrayOf(id.toString())
            )

            // 운동 루틴 삭제
            db.delete(
                "Routine",
                "routineId=?",
                arrayOf(id.toString())
            )

            db.setTransactionSuccessful()

        } finally {

            db.endTransaction()
        }
    }
}