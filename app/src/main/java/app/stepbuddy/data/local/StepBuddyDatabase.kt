package app.stepbuddy.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [GuideEntity::class, StepEntity::class, PairingEntity::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class StepBuddyDatabase : RoomDatabase() {
    abstract fun dao(): StepBuddyDao

    companion object {
        @Volatile private var instance: StepBuddyDatabase? = null

        fun get(context: Context): StepBuddyDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    StepBuddyDatabase::class.java,
                    "stepbuddy.db",
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
    }
}
