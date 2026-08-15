package dev.journeyops.application

import java.util.concurrent.ConcurrentHashMap

class LoanApplicationNotFoundException(id: String) : RuntimeException("Loan application $id was not found")

class LoanApplicationRepository {
    private val applications = ConcurrentHashMap<String, LoanApplication>()

    fun save(application: LoanApplication): LoanApplication {
        applications[application.id] = application
        return application
    }

    fun findById(id: String): LoanApplication? = applications[id]

    fun getById(id: String): LoanApplication = findById(id) ?: throw LoanApplicationNotFoundException(id)
}
