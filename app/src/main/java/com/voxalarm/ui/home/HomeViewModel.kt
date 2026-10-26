package com.voxalarm.ui.home

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.voxalarm.container
import com.voxalarm.data.Alarm
import com.voxalarm.data.AppSettings
import com.voxalarm.util.TimeText
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = app.container.repository

    val alarms: StateFlow<List<Alarm>?> =
        repo.alarms.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val settings: StateFlow<AppSettings> =
        app.container.settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    fun setEnabled(alarm: Alarm, enabled: Boolean) {
        viewModelScope.launch {
            val saved = repo.setEnabled(alarm, enabled)
            if (enabled) toast("${saved.label} rings ${TimeText.countdown(saved.nextTriggerAt)}")
        }
    }

    fun delete(alarm: Alarm) {
        viewModelScope.launch {
            repo.delete(alarm)
            toast("Deleted \"${alarm.label}\"")
        }
    }

    private fun toast(text: String) =
        Toast.makeText(getApplication(), text, Toast.LENGTH_SHORT).show()
}
