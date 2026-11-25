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
        root.addView(text("태블릿에서 부르면 주머니 속 폰이 핫스팟을 켭니다. 두 기기에 이 앱을 설치하고 같은 페어링 코드를 넣으세요.", 14));

        // Role
        LinearLayout roleCard = card(root, "이 기기의 역할");
        RadioGroup role = new RadioGroup(this);
        RadioButton tablet = new RadioButton(this);
        tablet.setText("부르는 쪽 (태블릿)");
        tablet.setId(View.generateViewId());
        RadioButton phone = new RadioButton(this);
        phone.setText("핫스팟을 켜는 쪽 (폰)");
        phone.setId(View.generateViewId());
        role.addView(tablet);
        role.addView(phone);
        role.check(Status.isPhone(this) ? phone.getId() : tablet.getId());
        role.setOnCheckedChangeListener((g, id) -> setRole(id == phone.getId()));
        roleCard.addView(role);

        // Pairing
        LinearLayout pairCard = card(root, "페어링 코드");
        pairCard.addView(text("두 기기에 같은 코드를 넣어야 서로 알아봅니다. 폰에서 만든 코드를 태블릿에 그대로 입력하거나, 원하는 코드(영문과 숫자 8자 이상)를 두 기기에 똑같이 넣으세요. 저장한 코드는 바꾸기 전까지 유지됩니다.", 13));
        code = new EditText(this);
        code.setSingleLine(true);
        code.setTypeface(Typeface.MONOSPACE);
        code.setTextSize(20);
        code.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        code.setText(Pairing.code(this));
        pairCard.addView(code);
        LinearLayout codeRow = row(pairCard);
        codeRow.addView(button("저장", v -> saveCode(code.getText().toString())));
        newCode = button("새 코드 만들기", v -> {
            code.setText(Pairing.generate());
            saveCode(code.getText().toString());
        });
        codeRow.addView(newCode);

        // Helper
        LinearLayout helperCard = card(root, "권한 도우미 (ADB 또는 Shizuku)");
        helperNote = text("", 13);
        helperCard.addView(helperNote);
        helperStatus = text("", 14);
        helperCard.addView(helperStatus);
        TextView command = text(Helper.adbCommand(this), 12);
        command.setTypeface(Typeface.MONOSPACE);
        command.setTextIsSelectable(true);
        helperCard.addView(text("ADB 방식: PC에 연결해 아래 명령을 실행합니다. 재부팅하면 다시 실행해야 합니다.", 13));
        helperCard.addView(command);
        LinearLayout helperRow = row(helperCard);
        helperRow.addView(button("명령 복사", v -> {
            getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("PocketSpot", Helper.adbCommand(this)));
            Toast.makeText(this, "복사했어요", Toast.LENGTH_SHORT).show();
        }));
        helperRow.addView(button("Shizuku 권한 요청", v -> requestShizuku()));
        helperCard.addView(button("상태 다시 확인", v -> update()));

        // Phone
        phoneCard = card(root, "폰");
        phoneStatus = text("", 14);
        phoneCard.addView(phoneStatus);
        phoneCard.addView(button("배터리 최적화에서 제외", v -> requestUnrestricted()));
        LinearLayout testRow = row(phoneCard);
        testRow.addView(button("핫스팟 켜기 (테스트)", v -> direct(true)));
        testRow.addView(button("끄기 (테스트)", v -> direct(false)));

        // Tablet
        tabletCard = card(root, "태블릿");
        tabletCard.addView(text("빠른 설정 편집에서 \"폰 핫스팟\" 타일을 추가하면 한 번에 부를 수 있어요.", 13));
        LinearLayout sendRow = row(tabletCard);
        sendRow.addView(button("핫스팟 켜기", v -> {
            Advertiser.callPhone(this, "앱");
            update();
        }));
        sendRow.addView(button("끄기", v -> {
            String error = Advertiser.send(this, Beacon.CMD_OFF);
            Status.log(this, error != null ? error : "끄기 신호 보냄");
            update();
        }));
        TextView preferTitle = text("폰 핫스팟 우선 연결", 16);
        preferTitle.setTypeface(Typeface.DEFAULT_BOLD);
        preferTitle.setPadding(0, dp(16), 0, 0);
        tabletCard.addView(preferTitle);
        tabletCard.addView(text("켜기 신호를 보낸 뒤 다른 와이파이에 붙어 있어도 폰 핫스팟으로 옮겨 붙습니다. 이 기기에도 권한 도우미가 필요합니다. 없으면 평소처럼 아는 와이파이 중에서 자동으로 붙습니다.", 13));
        Switch prefer = new Switch(this);
        prefer.setText("사용");
        prefer.setChecked(Status.preferHotspot(this));
        tabletCard.addView(prefer);
        EditText ssid = new EditText(this);
        ssid.setHint("폰 핫스팟 이름");
        ssid.setSingleLine(true);
        ssid.setText(Status.hotspotSsid(this));
        tabletCard.addView(ssid);
        EditText password = new EditText(this);
        password.setHint("핫스팟 비밀번호");
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
        tabletCard.addView(text("입력하는 즉시 저장됩니다.", 12));

        // Log
        LinearLayout logCard = card(root, "기록");
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
            Toast.makeText(this, "이미 제외되어 있어요", Toast.LENGTH_SHORT).show();
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
            Toast.makeText(this, "영문과 숫자로 " + Pairing.MIN_LENGTH + "자 이상 넣어 주세요", Toast.LENGTH_SHORT).show();
            return;
        }
        code.setText(Pairing.code(this));
        Toast.makeText(this, "저장했어요. 다른 기기에도 같은 코드를 넣어 주세요", Toast.LENGTH_SHORT).show();
    }

    private void update() {
        boolean phone = Status.isPhone(this);
        tabletCard.setVisibility(phone ? View.GONE : View.VISIBLE);
        phoneCard.setVisibility(phone ? View.VISIBLE : View.GONE);
        newCode.setVisibility(phone ? View.VISIBLE : View.GONE);
        helperNote.setText(phone
                ? "폰에서는 핫스팟을 켜기 위해 꼭 필요합니다. 일반 앱은 핫스팟을 켤 수 없어서, ADB 권한을 빌려 씁니다."
                : "태블릿에서는 \"폰 핫스팟 우선 연결\"을 쓸 때만 필요합니다.");
        log.setText(Status.log(this));
        helperStatus.setText("확인 중...");
        if (phone) {
            phoneStatus.setText("블루투스 감시: " + Status.scan(this) + "\n배터리 최적화: "
                    + (unrestricted() ? "제외됨" : "적용 중. 화면이 꺼져 있으면 핫스팟을 못 켤 수 있어요. 아래 버튼으로 제외해 주세요"));
        }
        new Thread(() -> {
            String adb = "pong".equals(Helper.adb("ping")) ? "실행 중" : "꺼져 있음";
            String shizuku = Helper.shizukuState();
            String text = "ADB 도우미: " + adb + "\nShizuku: " + shizuku;
            runOnUiThread(() -> helperStatus.setText(text));
        }).start();
    }

    private void direct(boolean on) {
        new Thread(() -> {
            String reply = Helper.tether(this, on);
            Status.log(this, "직접 " + (on ? "켜기" : "끄기") + ": " + reply);
            runOnUiThread(this::update);
        }).start();
    }

    private void requestShizuku() {
        try {
            if (!Shizuku.pingBinder()) {
                Toast.makeText(this, "Shizuku가 실행 중이 아니에요", Toast.LENGTH_SHORT).show();
            } else if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "이미 권한이 있어요", Toast.LENGTH_SHORT).show();
            } else {
                Shizuku.requestPermission(1);
            }
        } catch (Exception e) {
            Toast.makeText(this, "Shizuku 오류: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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
