package com.thedavelopers.eventqr.features.staff

import com.thedavelopers.eventqr.features.idprinting.model.dto.IdPrintResponse

interface IdPrintingContract {
    interface View {
        fun showPrintResult(message: String)
        fun renderLogs(items: List<IdPrintResponse>)
        fun showMessage(message: String)
        fun showLoading(isLoading: Boolean)
    }
}
