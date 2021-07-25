/*
 * Copyright 2018, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.trackmysleepquality.sleeptracker

import android.app.Application
import android.provider.SyncStateContract.Helpers.insert
import androidx.lifecycle.*
import com.example.android.trackmysleepquality.database.SleepDatabaseDao
import com.example.android.trackmysleepquality.database.SleepNight
import com.example.android.trackmysleepquality.formatNights
import kotlinx.coroutines.*

/**
 * ViewModel for SleepTrackerFragment.
 */
class SleepTrackerViewModel(
        val database: SleepDatabaseDao,
        application: Application) : AndroidViewModel(application) {
            //A job for coroutine,To manage all our coroutines
            private var viewModelJob= Job()


    override fun onCleared() {
        super.onCleared()
        viewModelJob.cancel()
    }
    //Now we need a scope for our coroutines to run in
    //The scope will determine what thread the coroutines will run on
    //it also needs to know about the job
    //To get a scope we ask for a an instance of coroutine Scope
    private val uiScope= CoroutineScope(Dispatchers.Main+viewModelJob)
    //Variable to hold the current night data
    private var tonight=MutableLiveData<SleepNight?>()

    //we also need all the nights from the viewmodel when we create the viewmodel
    private val nights=database.getAllNights()
    val nightsString = Transformations.map(nights) { nights ->
        formatNights(nights, application.resources)
    }
    val startButtonVisible = Transformations.map(tonight) {
        null == it
    }
    val stopButtonVisible = Transformations.map(tonight) {
        null != it
    }
    val clearButtonVisible = Transformations.map(nights) {
        it?.isNotEmpty()
    }
    private var _showSnackbarEvent = MutableLiveData<Boolean>()

    val showSnackBarEvent: LiveData<Boolean>
        get() = _showSnackbarEvent
    fun doneShowingSnackbar() {
        _showSnackbarEvent.value = false
    }

    private val _navigateToSleepQuality=MutableLiveData<SleepNight>()
    val navigateToSleepQuality :LiveData<SleepNight>
          get()=_navigateToSleepQuality
    fun doneNavigating(){
        _navigateToSleepQuality.value=null
    }
    init{
        initializeTonight()

    }

    private fun initializeTonight() {
        //inside we are using a coroutine to get tonight from the database
        //so that we are not blocking ui while waiting for the result
        uiScope.launch {
            tonight.value=getTonightFromDatabase()
        }
    }

    private suspend fun getTonightFromDatabase(): SleepNight? {

        return withContext(Dispatchers.IO) {
            var night=database.getTonight()
            if (night?.startTimeMilli!=night?.endTimeMilli)
            {
                night=null
            }
            night
        }
    }
     fun onStartTracking()
    {
        uiScope.launch {
            val newNight=SleepNight()
            insert(newNight)
            tonight.value=getTonightFromDatabase()
        }
    }
    private suspend fun insert(night: SleepNight)
    {

        withContext(Dispatchers.IO)
        {
            database.insert(night)
        }

    }
    fun onStopTracking() {
        uiScope.launch {
            val oldNight = tonight.value ?: return@launch
            oldNight.endTimeMilli = System.currentTimeMillis()
            update(oldNight)
            _navigateToSleepQuality.value=oldNight
        }
    }
    private suspend fun update(night: SleepNight) {
        withContext(Dispatchers.IO) {
            database.update(night)
        }
    }
    fun onClear() {
        uiScope.launch {
            clear()
            tonight.value = null
            _showSnackbarEvent.value = true

        }
    }

    suspend fun clear() {
        withContext(Dispatchers.IO) {
            database.clear()
        }
    }




}

