package com.example.fyp_25_s4_23.boundary.call

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.Surface
import com.example.fyp_25_s4_23.ui.theme.FYP25S423Theme
import com.example.fyp_25_s4_23.control.viewmodel.CallInProgressViewModel

class CallInProgressActivity : ComponentActivity() {
    private val viewModel: CallInProgressViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FYP25S423Theme {
                Surface {
                    CallInProgressScreen(
                        state = viewModel.state,
                        onAnswer = viewModel::answer,
                        onHangUp = viewModel::hangUp,
                        onMute = viewModel::toggleMute
                    )
                }
            }
        }
    }
}

