CORVIS TRANSLATE - Android projesi

YOL A (bilgisayarsiz, ucretsiz): GitHub Actions
1) github.com'da yeni (private olabilir) bir depo acin.
2) ZIP'in icindeki dosyalari (.github klasoru dahil) depoya yukleyin.
3) Actions sekmesinde "Build APK" calisir (5-8 dk). Bitince "CorvisTranslate-APK"
   artifact'ini indirin; icinden app-debug.apk cikar.
4) APK'yi telefona atin, "bilinmeyen kaynaklardan yuklemeye izin ver" deyip kurun.

YOL B (Android Studio): Klasoru acin > Build > Build APK(s).
Cikti: app/build/outputs/apk/debug/app-debug.apk

Not: Ceviri Google Translate'in herkese acik web uc noktasini kullanir (API anahtari gerekmez, resmi/garantili degildir).
Konusma tanima ve sesli okuma cihazdaki Google uygulamasi ve TTS dil paketlerine baglidir.
