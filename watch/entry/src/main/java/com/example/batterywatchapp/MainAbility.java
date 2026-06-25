package com.example.batterywatchapp;

import com.huawei.hms.wearengine.WearEngine;
import com.huawei.hms.wearengine.client.WearEngineClient;
import com.huawei.hms.wearengine.common.WearEngineException;
import com.huawei.hms.wearengine.message.MessageClient;
import com.huawei.hms.wearengine.message.MessageEvent;
import com.huawei.hms.wearengine.message.MessageListener;
import com.huawei.hms.wearengine.message.SendMessageCallback;
import com.huawei.hms.wearengine.node.CapabilityClient;
import com.huawei.hms.wearengine.node.Node;
import com.example.batterywatchapp.slice.MainAbilitySlice;
import ohos.aafwk.ability.Ability;
import ohos.aafwk.content.Intent;
import ohos.eventhandler.EventRunner;
import ohos.eventhandler.InnerEvent;
import ohos.hiviewdfx.HiLog;
import ohos.hiviewdfx.HiLogLabel;
import ohos.utils.zson.ZSONObject;

import java.nio.charset.StandardCharsets;
import java.util.List;

public class MainAbility extends Ability {
    private static final HiLogLabel LABEL = new HiLogLabel(HiLog.LOG_APP, 0x00201, "BatteryWatch");
    private static final String PATH_REQUEST = "/request_battery";
    private static final String PATH_RESPONSE = "/battery_response";
    private static final int MSG_TIMEOUT = 1;
    private static final long TIMEOUT_MS = 3000;

    private WearEngineClient client;
    private MainAbilitySlice slice;
    private MessageListener responseListener;
    private boolean isListenerRegistered = false;
    private boolean isPhoneConnected = false;
    private EventRunner runner = EventRunner.current();
    private MyEventHandler handler = new MyEventHandler(runner);

    @Override
    protected void onStart(Intent intent) {
        super.onStart(intent);
        HiLog.info(LABEL, "MainAbility onStart");
        // Инициализация Wear Engine
        WearEngine.initialize(getContext(), result ->
                HiLog.info(LABEL, "Wear Engine init result: " + result));
        client = WearEngine.getClient(getContext());

        // Регистрация слушателя ответов на сообщения
        registerMessageListener();

        // Получаем слайс, где находится интерфейс
        slice = (MainAbilitySlice) getMainSlice();
        if (slice == null) {
            setMainRoute(MainAbilitySlice.class.getName());
            slice = (MainAbilitySlice) getMainSlice();
        }

        // Проверяем подключение телефона
        checkPhoneConnection();

        // Автоматический запрос при старте
        requestBatteryUpdate();
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterMessageListener();
    }

    /**
     * Регистрирует слушатель сообщений с телефона по пути /battery_response.
     */
    private void registerMessageListener() {
        if (isListenerRegistered) return;
        responseListener = new MessageListener() {
            @Override
            public void onMessageReceived(MessageEvent messageEvent) {
                if (PATH_RESPONSE.equals(messageEvent.getPath())) {
                    handler.removeEvent(MSG_TIMEOUT);
                    String json = new String(messageEvent.getData(), StandardCharsets.UTF_8);
                    HiLog.info(LABEL, "Received battery response: " + json);
                    ZSONObject data = ZSONObject.stringToZSON(json);
                    int level = data.getIntValue("level", -1);
                    boolean charging = data.getBooleanValue("charging", false);
                    // Обновляем UI в главном потоке
                    getUITaskDispatcher().asyncDispatch(() -> {
                        if (slice != null) {
                            slice.updateBattery(level, charging);
                            slice.showMessage("Обновлено только что");
                        }
                    });
                }
            }
        };
        client.getMessageClient().addListener(responseListener).addOnSuccessListener(aVoid -> {
            isListenerRegistered = true;
            HiLog.info(LABEL, "Message listener registered");
        }).addOnFailureListener(e -> HiLog.error(LABEL, "Failed to register listener: " + e));
    }

    private void unregisterMessageListener() {
        if (isListenerRegistered && responseListener != null) {
            client.getMessageClient().removeListener(responseListener);
            isListenerRegistered = false;
        }
    }

    /**
     * Проверяет наличие подключённого телефона (узла).
     */
    private void checkPhoneConnection() {
        client.getNodeClient().getConnectedNodes().addOnSuccessListener(nodes -> {
            boolean phoneFound = false;
            for (Node node : nodes) {
                if (node.isConnected()) {
                    phoneFound = true;
                    break;
                }
            }
            isPhoneConnected = phoneFound;
            HiLog.info(LABEL, "Phone connected: " + phoneFound);
            if (slice != null) {
                getUITaskDispatcher().asyncDispatch(() -> {
                    if (!phoneFound) {
                        slice.showMessage("Телефон не подключен");
                        slice.setEnabled(false);
                    } else {
                        slice.setEnabled(true);
                    }
                });
            }
        }).addOnFailureListener(e -> {
            isPhoneConnected = false;
            getUITaskDispatcher().asyncDispatch(() -> slice.showMessage("Ошибка проверки подключения"));
        });

        // Слушаем события подключения/отключения узлов (можно добавить NodeListener)
    }

    /**
     * Отправляет запрос заряда на телефон.
     */
    void requestBatteryUpdate() {
        if (!isPhoneConnected) {
            getUITaskDispatcher().asyncDispatch(() -> {
                if (slice != null) slice.showMessage("Телефон не подключен");
            });
            return;
        }
        // Отправляем пустое сообщение-запрос
        byte[] emptyData = new byte[0];
        client.getMessageClient().sendMessage(PATH_REQUEST, emptyData, new SendMessageCallback() {
            @Override
            public void onSuccess(Void aVoid) {
                HiLog.info(LABEL, "Request sent successfully");
                // Запускаем таймер ожидания ответа
                handler.sendEvent(InnerEvent.get(MSG_TIMEOUT), TIMEOUT_MS);
            }

            @Override
            public void onFailure(WearEngineException e) {
                HiLog.error(LABEL, "Failed to send request: " + e.getMessage());
                getUITaskDispatcher().asyncDispatch(() -> {
                    if (slice != null) slice.showMessage("Телефон не отвечает");
                });
            }
        });
    }

    private class MyEventHandler extends ohos.eventhandler.EventHandler {
        public MyEventHandler(EventRunner runner) {
            super(runner);
        }

        @Override
        protected void processEvent(InnerEvent event) {
            if (event.eventId == MSG_TIMEOUT) {
                getUITaskDispatcher().asyncDispatch(() -> {
                    if (slice != null) slice.showMessage("Телефон не отвечает");
                });
            }
        }
    }
}
