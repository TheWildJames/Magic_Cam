package com.virtualcamera.manager.utils

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader

object RootUtils {
    private const val TAG = "RootUtils"
    
    suspend fun checkRootAccess(): Boolean = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec("su")
            val outputStream = DataOutputStream(process.outputStream)
            
            outputStream.writeBytes("id\n")
            outputStream.writeBytes("exit\n")
            outputStream.flush()
            
            val exitCode = process.waitFor()
            
            if (exitCode == 0) {
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val output = reader.readLine()
                reader.close()
                
                val hasRoot = output?.contains("uid=0") == true
                Log.d(TAG, "Root check result: $hasRoot")
                return@withContext hasRoot
            }
            
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking root access", e)
            false
        }
    }
    
    suspend fun executeRootCommand(command: String): String = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Executing root command: $command")
            
            val process = Runtime.getRuntime().exec("su")
            val outputStream = DataOutputStream(process.outputStream)
            
            outputStream.writeBytes("$command\n")
            outputStream.writeBytes("exit\n")
            outputStream.flush()
            
            val exitCode = process.waitFor()
            
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            reader.close()
            
            if (exitCode != 0) {
                val errorReader = BufferedReader(InputStreamReader(process.errorStream))
                val errorOutput = StringBuilder()
                while (errorReader.readLine().also { line = it } != null) {
                    errorOutput.append(line).append("\n")
                }
                errorReader.close()
                
                Log.e(TAG, "Command failed with exit code $exitCode: $errorOutput")
                return@withContext "ERROR: $errorOutput"
            }
            
            val result = output.toString().trim()
            Log.d(TAG, "Command output: $result")
            result
            
        } catch (e: Exception) {
            Log.e(TAG, "Error executing root command: $command", e)
            "ERROR: ${e.message}"
        }
    }
    
    suspend fun executeRootCommands(commands: List<String>): List<String> = withContext(Dispatchers.IO) {
        val results = mutableListOf<String>()
        
        try {
            val process = Runtime.getRuntime().exec("su")
            val outputStream = DataOutputStream(process.outputStream)
            
            for (command in commands) {
                Log.d(TAG, "Executing: $command")
                outputStream.writeBytes("$command\n")
            }
            outputStream.writeBytes("exit\n")
            outputStream.flush()
            
            val exitCode = process.waitFor()
            
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            reader.close()
            
            results.add(output.toString().trim())
            
        } catch (e: Exception) {
            Log.e(TAG, "Error executing root commands", e)
            results.add("ERROR: ${e.message}")
        }
        
        results
    }
}