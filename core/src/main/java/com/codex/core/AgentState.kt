package com.codex.core

/**
 * Represents the state of the agent loop.
 */
sealed class AgentState {
    data object Idle : AgentState()
    data object Running : AgentState()
    data class Error(val message: String) : AgentState()
    
    val status: String
        get() = when (this) {
            is Idle -> "Idle"
            is Running -> "Running"
            is Error -> "Error"
        }
}

data class AgentStateExtended(
    val base: AgentState,
    val iteration: Int = 0,
    val status: String = "Idle"
) {
    companion object {
        fun Idle() = AgentStateExtended(AgentState.Idle)
        fun Running(iteration: Int = 0) = AgentStateExtended(AgentState.Running, iteration, "Running")
        fun Error(message: String) = AgentStateExtended(AgentState.Error(message))
    }
    
    val isIdle: Boolean get() = base is AgentState.Idle
    val isError: Boolean get() = base is AgentState.Error
    val isRunning: Boolean get() = base is AgentState.Running
    
    fun copy(
        base: AgentState = this.base,
        iteration: Int = this.iteration,
        status: String = this.status
    ) = AgentStateExtended(base, iteration, status)
}