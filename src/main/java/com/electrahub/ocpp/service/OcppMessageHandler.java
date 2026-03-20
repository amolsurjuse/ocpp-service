package com.electrahub.ocpp.service;

import com.fasterxml.jackson.databind.JsonNode;

public interface OcppMessageHandler {

    String getAction();

    JsonNode handle(String chargePointId, JsonNode payload);

}
