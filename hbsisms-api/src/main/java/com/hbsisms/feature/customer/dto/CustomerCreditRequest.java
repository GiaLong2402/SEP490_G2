package com.hbsisms.feature.customer.dto;

import java.math.BigDecimal;

/** Body of PATCH /customers/{id}/credit — task 72. */
public record CustomerCreditRequest(Boolean creditEnabled, BigDecimal creditLimit, Short netDays) {}
