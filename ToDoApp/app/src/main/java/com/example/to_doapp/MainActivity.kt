package com.example.to_doapp

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.room.*
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.to_doapp.ui.theme.ToDoAppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// 1. Entity: Represents the tasks table in SQLite using Room
@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val isDone: Boolean = false
)

// 2. DAO: Data Access Object defines DB operations
@Dao
interface TaskDao {

    // Return all tasks as a Flow to observe real-time changes
    @Query("SELECT * FROM tasks ORDER BY id DESC")
    fun getAll(): Flow<List<Task>>

    // Insert task (replace if conflict)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: Task)

    // Update task record
    @Update
    suspend fun update(task: Task)

    // Delete task record
    @Delete
    suspend fun delete(task: Task)
}

// 3. Room Database definition
//from lecture example
@Database(entities = [Task::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        // Create or return singleton instance of DB
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "task_db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}


// 4. ViewModel: Handles DB operations + state tracking
class TaskViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = AppDatabase.getDatabase(application).taskDao()
    val tasks: Flow<List<Task>> = dao.getAll() // list of tasks

    private val _lastUpdatedId = MutableStateFlow<Int?>(null) // Track last updated task ID
    val lastUpdatedId: StateFlow<Int?> = _lastUpdatedId

    // Add new task
    fun addTask(title: String) {
        viewModelScope.launch {
            dao.insert(Task(title = title))
        }
    }

    // Toggle isDone state
    fun toggleTask(task: Task) {
        viewModelScope.launch {
            //If isDone was true, it becomes false and vice versa.
            val updated = task.copy(isDone = !task.isDone)
            dao.update(updated)
            _lastUpdatedId.value = updated.id
            delay(2000)
            _lastUpdatedId.value = null
        }
    }

    // Edit task title
    fun updateTask(task: Task, newTitle: String) {
        viewModelScope.launch {
            val updated = task.copy(title = newTitle)
            dao.update(updated)
            _lastUpdatedId.value = updated.id
            delay(2000)
            _lastUpdatedId.value = null
        }
    }

    // Delete task
    fun deleteTask(task: Task) {
        viewModelScope.launch {
            dao.delete(task)
        }
    }
}


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ToDoAppTheme {
                val context = LocalContext.current
                val viewModel: TaskViewModel = viewModel(
                    factory = ViewModelProvider.AndroidViewModelFactory.getInstance(
                        context.applicationContext as Application
                    )
                )
                TaskScreen(viewModel)
            }
        }
    }
}

@Composable
fun TaskScreen(viewModel: TaskViewModel) {
    val tasks by viewModel.tasks.collectAsState(initial = emptyList())
    val lastUpdatedId by viewModel.lastUpdatedId.collectAsState()

    var newTitle by remember { mutableStateOf("") }

    var filter by remember { mutableStateOf("All") }
    val filteredTasks = when (filter) {
        "Completed" -> tasks.filter { it.isDone }
        "Pending" -> tasks.filter { !it.isDone }
        else -> tasks
    }


    //add new task - text field and button to submit/add task
    Column(
        modifier = Modifier.padding(16.dp).fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally)
    {

        Text(
            "To-Do List",
            modifier = Modifier
                .padding(top = 16.dp),
            style = MaterialTheme.typography.headlineMedium
        )


        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        ) {
            OutlinedTextField(
                value = newTitle,
                onValueChange = { newTitle = it },
                label = { Text("New Task") },
                modifier = Modifier
                    .width(300.dp)
            )

            Button(
                onClick = {
                    if (newTitle.isNotBlank()) {
                        viewModel.addTask(newTitle)
                        newTitle = ""
                    }
                },
                modifier = Modifier
                    .width(90.dp)
                    .height(60.dp)
                    .padding(1.dp)
                    .padding(top = 10.dp)

            ) {
                Text("Add Task")
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 12.dp))

        //display filter buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(onClick = { filter = "All" }) {
                Text("All")
            }
            Button(onClick = { filter = "Completed" }) {
                Text("Completed")
            }
            Button(onClick = { filter = "Pending" }) {
                Text("Pending")
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 12.dp))

        //display the saved tasks
        LazyColumn {
            items(filteredTasks) { task ->
                var updatedTitle by remember(task.id) { mutableStateOf(task.title) }

                //display checkbox, task title and buttons for update and delete functions
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {

                    //task display with checkbox
                    Row(modifier = Modifier.weight(1f)) {
                        Checkbox(
                            checked = task.isDone,
                            onCheckedChange = {
                                viewModel.toggleTask(task)
                            }
                        )

                        Column {
                            OutlinedTextField(
                                value = updatedTitle,
                                onValueChange = { updatedTitle = it },
                                label = { Text("Task Title") },
                                singleLine = true
                            )

                            if (task.id == lastUpdatedId) {
                                Text("Updated!", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    //buttons next to task
                    Column {
                        Button(
                            onClick = {
                                if (updatedTitle.isNotBlank()) {
                                    viewModel.updateTask(task, updatedTitle)
                                }
                            },
                            modifier = Modifier
                                .width(80.dp)
                                .height(30.dp)
                                .padding(1.dp)
                        ) {
                            Text(
                                text = "Update",
                                fontSize = 10.sp,
                                maxLines = 1
                            )
                        }

                        Spacer(Modifier.height(4.dp))

                        Button(
                            onClick = { viewModel.deleteTask(task) },
                            modifier = Modifier
                                .width(80.dp)
                                .height(30.dp)
                                .padding(1.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                        ) {
                            Text("Delete",
                                fontSize = 10.sp,
                                maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}


@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    ToDoAppTheme {
    }
}