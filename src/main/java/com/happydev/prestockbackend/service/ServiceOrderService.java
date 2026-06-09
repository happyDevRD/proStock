package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.dto.*;
import com.happydev.prestockbackend.entity.ServiceOrderStatus;
import com.happydev.prestockbackend.entity.ServiceOrderType;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.util.List;

public interface ServiceOrderService {

    List<ServiceOrderDto> findAll();

    List<ServiceOrderDto> findByType(@NonNull ServiceOrderType type);

    List<ServiceOrderDto> findActiveOrders();

    List<ServiceOrderDto> findByCustomer(@NonNull Long customerId);

    ServiceOrderDto findById(@NonNull Long id);

    ServiceOrderDto create(@NonNull CreateServiceOrderRequest request, @Nullable String actor);

    ServiceOrderDto update(@NonNull Long id, @NonNull UpdateServiceOrderRequest request, @Nullable String actor);

    ServiceOrderDto advanceStage(@NonNull Long id, @NonNull AdvanceStageRequest request, @Nullable String actor);

    ServiceOrderDto approveBudget(@NonNull Long id, @Nullable String actor);

    ServiceOrderDto cancel(@NonNull Long id, @Nullable String notes, @Nullable String actor);

    ServiceOrderDto complete(@NonNull Long id, @Nullable String actor);

    ServiceOrderNoteDto addNote(@NonNull Long id, @NonNull AddNoteRequest request, @Nullable String actor);

    void deleteNote(@NonNull Long orderId, @NonNull Long noteId);

    ServiceOrderItemDto addItem(@NonNull Long orderId, @NonNull AddServiceOrderItemRequest request);

    void removeItem(@NonNull Long orderId, @NonNull Long itemId);
}
