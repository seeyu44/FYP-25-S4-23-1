package com.example.fyp_25_s4_23.control.controllers

import com.example.fyp_25_s4_23.control.usecases.GetSystemUptime


class SystemController {
    private val getUptime = GetSystemUptime()


    fun fetchUptime(): String = getUptime()
}
