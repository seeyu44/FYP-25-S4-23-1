package com.example.fyp_25_s4_23.control.usecases

import com.example.fyp_25_s4_23.control.controllers.DetectionController

class StartMonitoring(private val controller: DetectionController) {
    operator fun invoke() = controller.startMonitoring()
}

