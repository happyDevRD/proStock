package com.happydev.prestockbackend.dto;

import java.util.List;

public record SetupWorkbookResult(
        boolean companyUpdated,
        int usersCreated,
        int usersSkipped,
        int categoriesCreated,
        int suppliersCreated,
        int productsImported,
        int productsSkipped,
        List<String> warnings
) {
}
