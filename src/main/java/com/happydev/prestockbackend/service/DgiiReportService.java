package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.dto.Dgii606ReportDto;
import com.happydev.prestockbackend.dto.Dgii607ReportDto;
import com.happydev.prestockbackend.dto.Dgii608ReportDto;
import org.springframework.lang.NonNull;

import java.time.YearMonth;

/**
 * Genera los reportes de cumplimiento DGII (Norma 07-18):
 * 606 (compras), 607 (ventas de bienes y servicios) y 608 (NCF anulados).
 */
public interface DgiiReportService {
    Dgii606ReportDto build606(@NonNull YearMonth period);

    /** Reporte 606 en el formato de envío TXT (pipe-delimited) de la DGII. */
    String render606Txt(@NonNull YearMonth period);

    Dgii607ReportDto build607(@NonNull YearMonth period);

    Dgii608ReportDto build608(@NonNull YearMonth period);

    /** Reporte 607 en el formato de envío TXT (pipe-delimited) de la DGII. */
    String render607Txt(@NonNull YearMonth period);

    /** Reporte 608 en el formato de envío TXT (pipe-delimited) de la DGII. */
    String render608Txt(@NonNull YearMonth period);
}
