package com.example.batterywatchapp.slice;

import com.example.batterywatchapp.MainAbility;
import com.example.batterywatchapp.ResourceTable;
import ohos.aafwk.ability.AbilitySlice;
import ohos.aafwk.content.Intent;
import ohos.agp.components.Button;
import ohos.agp.components.Component;
import ohos.agp.components.Text;
import ohos.agp.components.ProgressBar;
import ohos.agp.window.dialog.ToastDialog;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainAbilitySlice extends AbilitySlice {
    private Text batteryPercent;
    private Text statusText;
    private Text lastUpdateText;
    private Button updateButton;
    private ProgressBar loadingIndicator;
    private MainAbility mainAbility;

    @Override
    protected void onStart(Intent intent) {
        super.onStart(intent);
        super.setUIContent(ResourceTable.Layout_ability_main);

        batteryPercent = (Text) findComponentById(ResourceTable.Id_battery_percent);
        statusText = (Text) findComponentById(ResourceTable.Id_status_text);
        lastUpdateText = (Text) findComponentById(ResourceTable.Id_last_update);
        updateButton = (Button) findComponentById(ResourceTable.Id_update_button);
        loadingIndicator = (ProgressBar) findComponentById(ResourceTable.Id_loading_indicator);

        mainAbility = (MainAbility) getAbility();

        updateButton.setClickedListener(component -> {
            // Показать индикатор загрузки
            loadingIndicator.setVisibility(Component.VISIBLE);
            updateButton.setEnabled(false);
            mainAbility.requestBatteryUpdate();
            // Индикатор будет скрыт при получении ответа или таймауте (через slice)
        });

        // Начальное состояние
        batteryPercent.setText("--");
        statusText.setText("");
        lastUpdateText.setText("");
    }

    /**
     * Вызывается из MainAbility для обновления данных.
     */
    public void updateBattery(int level, boolean charging) {
        batteryPercent.setText(level + "%");
        if (charging) {
            statusText.setText("Заряжается");
        } else {
            statusText.setText("От батареи");
        }
        String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
        lastUpdateText.setText("Обновлено " + time);
        loadingIndicator.setVisibility(Component.INVISIBLE);
        updateButton.setEnabled(true);
    }

    /**
     * Показать сообщение (ошибка или предупреждение).
     */
    public void showMessage(String message) {
        lastUpdateText.setText(message);
        loadingIndicator.setVisibility(Component.INVISIBLE);
        updateButton.setEnabled(true);
        new ToastDialog(getContext()).setText(message).setDuration(2000).show();
    }

    /**
     * Блокировка/разблокировка кнопки обновления.
     */
    public void setEnabled(boolean enabled) {
        updateButton.setEnabled(enabled);
    }
}
