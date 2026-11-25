# libs

Shizuku 클라이언트 라이브러리. `build.sh`가 앱에 함께 넣음.

| 파일 | 출처 (Maven Central) | AAR SHA-1 |
|---|---|---|
| `shizuku-api-13.1.5.jar` | `dev.rikka.shizuku:api:13.1.5` | `1f760c56493bdbc01462930e75bb325033b36fe2` |
| `shizuku-provider-13.1.5.jar` | `dev.rikka.shizuku:provider:13.1.5` | `4135b99cae9362fe2f63380230d04a9bcba9be3f` |
| `shizuku-aidl-13.1.5.jar` | `dev.rikka.shizuku:aidl:13.1.5` | `457504c277d796adea698b80b4e765f98dd0799a` |
| `shizuku-shared-13.1.5.jar` | `dev.rikka.shizuku:shared:13.1.5` | `8c4e73ce466cd27aff10565d2ec49809c258f974` |

- 각 jar는 해당 AAR 안의 `classes.jar`
- AAR에 리소스는 없음. provider AAR의 manifest 항목(권한, meta-data, provider)은 이 앱의 `AndroidManifest.xml`에 직접 옮겨 둠
- 라이선스: MIT ([Shizuku-API](https://github.com/RikkaApps/Shizuku-API), `LICENSE-Shizuku-API`)
