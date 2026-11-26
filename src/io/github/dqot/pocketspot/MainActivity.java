package io.github.dqot.pocketspot;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import rikka.shizuku.Shizuku;

/** One screen for both roles: the tablet calls, the phone turns its hotspot on. */
public final class MainActivity extends Activity {
    private static final String[] PERMISSIONS = {
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
    };

    private EditText code;
    private Button newCode;
    private TextView helperStatus;
    private TextView phoneStatus;
    private TextView log;
    private LinearLayout tabletCard;
    private LinearLayout phoneCard;
    private TextView helperNote;
    private final Shizuku.OnRequestPermissionResultListener shizukuResult = (requestCode, result) ->
            runOnUiThread(this::update);

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Helper.writeStartScript(this);
        int pad = dp(20);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad * 2);

        TextView title = text("PocketSpot", 26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);
        root.addView(text(getString(R.string.intro), 14));

        // Role
        LinearLayout roleCard = card(root, getString(R.string.role_title));
        RadioGroup role = new RadioGroup(this);
        RadioButton tablet = new RadioButton(this);
        tablet.setText(R.string.role_tablet);
        tablet.setId(View.generateViewId());
        RadioButton phone = new RadioButton(this);
        phone.setText(R.string.role_phone);
        phone.setId(View.generateViewId());
        role.addView(tablet);
        role.addView(phone);
        role.check(Status.isPhone(this) ? phone.getId() : tablet.getId());
        role.setOnCheckedChangeListener((g, id) -> setRole(id == phone.getId()));
        roleCard.addView(role);

        // Pairing
        LinearLayout pairCard = card(root, getString(R.string.pairing_title));
        pairCard.addView(text(getString(R.string.pairing_help), 13));
        code = new EditText(this);
        code.setSingleLine(true);
        code.setTypeface(Typeface.MONOSPACE);
        code.setTextSize(20);
        code.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        code.setText(Pairing.code(this));
        pairCard.addView(code);
        LinearLayout codeRow = row(pairCard);
        codeRow.addView(button(getString(R.string.save), v -> saveCode(code.getText().toString())));
        newCode = button(getString(R.string.new_code), v -> {
            code.setText(Pairing.generate());
            saveCode(code.getText().toString());
        });
        codeRow.addView(newCode);

        // Helper
        LinearLayout helperCard = card(root, getString(R.string.helper_title));
        helperNote = text("", 13);
        helperCard.addView(helperNote);
        helperStatus = text("", 14);
        helperCard.addView(helperStatus);
        TextView command = text(Helper.adbCommand(this), 12);
        command.setTypeface(Typeface.MONOSPACE);
        command.setTextIsSelectable(true);
        helperCard.addView(text(getString(R.string.adb_help), 13));
        helperCard.addView(command);
        LinearLayout helperRow = row(helperCard);
        helperRow.addView(button(getString(R.string.copy_command), v -> {
            getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("PocketSpot", Helper.adbCommand(this)));
            Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show();
        }));
        helperRow.addView(button(getString(R.string.request_shizuku), v -> requestShizuku()));
        helperCard.addView(button(getString(R.string.recheck), v -> update()));

        // Phone
        phoneCard = card(root, getString(R.string.phone_title));
        phoneStatus = text("", 14);
        phoneCard.addView(phoneStatus);
        phoneCard.addView(button(getString(R.string.battery_exempt_button), v -> requestUnrestricted()));
        LinearLayout testRow = row(phoneCard);
        testRow.addView(button(getString(R.string.test_on), v -> direct(true)));
        testRow.addView(button(getString(R.string.test_off), v -> direct(false)));

        // Tablet
        tabletCard = card(root, getString(R.string.tablet_title));
        tabletCard.addView(text(getString(R.string.tile_hint), 13));
        LinearLayout sendRow = row(tabletCard);
        sendRow.addView(button(getString(R.string.hotspot_on), v -> {
            Advertiser.callPhone(this, getString(R.string.via_app));
            update();
        }));
        sendRow.addView(button(getString(R.string.hotspot_off), v -> {
            String error = Advertiser.send(this, Beacon.CMD_OFF);
            Status.log(this, error != null ? error : getString(R.string.log_off_sent));
            update();
        }));
        TextView preferTitle = text(getString(R.string.prefer_title), 16);
        preferTitle.setTypeface(Typeface.DEFAULT_BOLD);
        preferTitle.setPadding(0, dp(16), 0, 0);
        tabletCard.addView(preferTitle);
        tabletCard.addView(text(getString(R.string.prefer_help), 13));
        Switch prefer = new Switch(this);
        prefer.setText(R.string.prefer_switch);
        prefer.setChecked(Status.preferHotspot(this));
        tabletCard.addView(prefer);
        EditText ssid = new EditText(this);
        ssid.setHint(R.string.ssid_hint);
        ssid.setSingleLine(true);
        ssid.setText(Status.hotspotSsid(this));
        tabletCard.addView(ssid);
        EditText password = new EditText(this);
        password.setHint(R.string.password_hint);
        password.setSingleLine(true);
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        password.setText(Status.hotspotPassword(this));
        tabletCard.addView(password);
        // Saved as soon as anything changes: no separate save button to forget
        Runnable saveHotspot = () -> Status.setHotspot(this, prefer.isChecked(),
                ssid.getText().toString().trim(), password.getText().toString());
        prefer.setOnCheckedChangeListener((v, on) -> saveHotspot.run());
        android.text.TextWatcher watcher = new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
                saveHotspot.run();
            }
        };
        ssid.addTextChangedListener(watcher);
        password.addTextChangedListener(watcher);
        tabletCard.addView(text(getString(R.string.saved_instantly), 12));

        // Log
        LinearLayout logCard = card(root, getString(R.string.log_title));
        log = text("", 12);
        log.setTypeface(Typeface.MONOSPACE);
        logCard.addView(log);

        ScrollView scroll = new ScrollView(this);
        scroll.setFitsSystemWindows(true);
        scroll.addView(root);
        setContentView(scroll);
        Shizuku.addRequestPermissionResultListener(shizukuResult);
        requestMissingPermissions();
    }

    @Override
    protected void onDestroy() {
        Shizuku.removeRequestPermissionResultListener(shizukuResult);
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        update();
    }

    private void setRole(boolean phone) {
        Status.setPhone(this, phone);
        if (phone) {
            if (Pairing.key(this) == null) {
                code.setText(Pairing.generate());
                saveCode(code.getText().toString());
            }
            Status.setScan(this, Scanner.register(this));
            if (!unrestricted()) requestUnrestricted();
        } else {
            Scanner.stop(this);
        }
        update();
    }

    /**
     * In Doze the phone cuts network access for apps that are not exempt, and that includes the
     * 127.0.0.1 link to the ADB helper: the call arrives but never reaches the helper.
     */
    private boolean unrestricted() {
        return getSystemService(android.os.PowerManager.class).isIgnoringBatteryOptimizations(getPackageName());
    }

    private void requestUnrestricted() {
        if (unrestricted()) {
            Toast.makeText(this, R.string.already_exempt, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            startActivity(new android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    android.net.Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            startActivity(new android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        }
    }

    private void saveCode(String value) {
        if (!Pairing.setCode(this, value)) {
            Toast.makeText(this, getString(R.string.code_too_short, Pairing.MIN_LENGTH), Toast.LENGTH_SHORT).show();
            return;
        }
        code.setText(Pairing.code(this));
        Toast.makeText(this, R.string.code_saved, Toast.LENGTH_SHORT).show();
    }

    private void update() {
        boolean phone = Status.isPhone(this);
        tabletCard.setVisibility(phone ? View.GONE : View.VISIBLE);
        phoneCard.setVisibility(phone ? View.VISIBLE : View.GONE);
        newCode.setVisibility(phone ? View.VISIBLE : View.GONE);
        helperNote.setText(phone ? R.string.helper_note_phone : R.string.helper_note_tablet);
        log.setText(Status.log(this));
        helperStatus.setText(R.string.checking);
        if (phone) {
            phoneStatus.setText(getString(R.string.phone_status, Status.scan(this),
                    getString(unrestricted() ? R.string.battery_exempt : R.string.battery_restricted)));
        }
        new Thread(() -> {
            String adb = getString("pong".equals(Helper.adb("ping")) ? R.string.adb_running : R.string.adb_stopped);
            String shizuku = Helper.shizukuState(this);
            String text = getString(R.string.helper_status, adb, shizuku);
            runOnUiThread(() -> helperStatus.setText(text));
        }).start();
    }

    private void direct(boolean on) {
        new Thread(() -> {
            String reply = Helper.tether(this, on);
            Status.log(this, getString(R.string.log_direct,
                    getString(on ? R.string.action_on : R.string.action_off), reply));
            runOnUiThread(this::update);
        }).start();
    }

    private void requestShizuku() {
        try {
            if (!Shizuku.pingBinder()) {
                Toast.makeText(this, R.string.shizuku_not_running_toast, Toast.LENGTH_SHORT).show();
            } else if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, R.string.already_granted, Toast.LENGTH_SHORT).show();
            } else {
                Shizuku.requestPermission(1);
            }
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.shizuku_error_toast, e.getMessage()), Toast.LENGTH_SHORT).show();
        }
    }

    private void requestMissingPermissions() {
        for (String p : PERMISSIONS) {
            if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(PERMISSIONS, 1);
                return;
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        if (Status.isPhone(this)) Status.setScan(this, Scanner.register(this));
        update();
    }

    // Small layout helpers

    private LinearLayout card(LinearLayout parent, String title) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(12), dp(16), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(16));
        bg.setColor(0x14808080);
        card.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(16);
        parent.addView(card, lp);
        TextView t = text(title, 17);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(t);
        return card;
    }

    private LinearLayout row(LinearLayout parent) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        parent.addView(row);
        return row;
    }

    private TextView text(String s, int sp) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setPadding(0, dp(4), 0, dp(4));
        return t;
    }

    private Button button(String label, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setOnClickListener(listener);
        return b;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
