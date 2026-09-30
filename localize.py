from pathlib import Path
from xml.sax.saxutils import escape
import json

root = Path(__file__).parent
# Key, English, Simplified Chinese, Malay. English is the fallback language.
strings = [
('settings','Settings','设置','Tetapan'),
('about','About','关于','Perihal'),
('profile_image','Jacob7179 profile image','Jacob7179 的头像','Gambar profil Jacob7179'),
('app_version','Version %1$s','版本 %1$s','Versi %1$s'),
('app_description','Save multiple websites and switch between them in an Android WebView. Website cookies, localStorage, and IndexedDB stay on your device.','在 Android WebView 中保存多个网站并随时切换。网站 Cookie、localStorage 和 IndexedDB 数据保存在此设备上。','Simpan beberapa laman web dan bertukar antaranya dalam Android WebView. Kuki laman, localStorage dan IndexedDB disimpan pada peranti anda.'),
('app_details','Android 8.0 or later\\nEnglish · 简体中文 · Bahasa Melayu\\nDark mode: Auto / On / Off\\nInternet connection required to load online pages.','Android 8.0 或更高版本\\nEnglish · 简体中文 · Bahasa Melayu\\n深色模式：自动 / 开启 / 关闭\\n加载在线网页需要网络连接。','Android 8.0 atau lebih baharu\\nEnglish · 简体中文 · Bahasa Melayu\\nMod gelap: Auto / Hidup / Mati\\nSambungan internet diperlukan untuk memuatkan halaman dalam talian.'),
('github_profile','Open Jacob7179 on GitHub','在 GitHub 上查看 Jacob7179','Buka Jacob7179 di GitHub'),
('no_browser','No app is available to open this link.','没有可打开此链接的应用。','Tiada aplikasi tersedia untuk membuka pautan ini.'),
('appearance','Dark mode','深色模式','Mod gelap'),
('theme_system','Auto','自动','Auto'),
('theme_light','Off','关闭','Mati'),
('theme_dark','On','开启','Hidup'),
('tagline','Your personal web space','你的专属网页空间','Ruang web peribadi anda'),
('add_site','+ Add site','+ 添加网站','+ Tambah'),
('choose_site','Choose a website','选择网站','Pilih laman web'),
('reload','Reload page','重新加载网页','Muat semula halaman'),
('empty_title','A home for your websites','为常用网站安个家','Semua laman di satu tempat'),
('empty_hint','Keep your favorite sites together.\nPick a site and carry on where you left off.','集中收藏你喜欢的网站。\n选择一个网站，继续上次的浏览。','Simpan laman kegemaran anda bersama.\nPilih laman untuk meneruskan pelayaran.'),
('first_site','+  Add your first site','+ 添加第一个网站','+ Tambah laman pertama'),
('local_data','Website data stays on this device','网站数据保存在此设备上','Data laman disimpan pada peranti ini'),
('back','Back','后退','Kembali'),
('forward','Forward','前进','Ke depan'),
('home','Home','主页','Utama'),
('sites','Sites','网站','Laman'),
('external_link','This link requires another app.','此链接需要通过其他应用打开。','Pautan ini memerlukan aplikasi lain.'),
('load_error','Could not load this page. Check your connection and URL, then tap Reload.','无法加载此网页。请检查网络连接和网址，然后点击重新加载。','Halaman tidak dapat dimuatkan. Semak sambungan dan URL, kemudian muat semula.'),
('your_sites','Your sites','我的网站','Laman anda'),
('done','Done','完成','Selesai'),
('in_use','IN USE','正在使用','SEDANG DIGUNAKAN'),
('edit','Edit','编辑','Edit'),
('form_hint','Enter a website address to keep it in your shelf.','输入网址，将网站加入收藏。','Masukkan alamat laman web untuk menyimpannya.'),
('url_label','WEBSITE URL','网站地址','URL LAMAN WEB'),
('url_accessible','Website URL','网站地址','URL laman web'),
('scheme_hint','No https://? We’ll add it for you.','未输入 https://？我们会自动添加。','Tiada https://? Kami akan menambahkannya.'),
('add_title','Add a site','添加网站','Tambah laman'),
('edit_title','Edit site','编辑网站','Edit laman'),
('save_open','Save & open','保存并打开','Simpan & buka'),
('cancel','Cancel','取消','Batal'),
('remove','Remove','移除','Buang'),
('remove_title','Remove saved URL?','移除已保存的网址？','Buang URL yang disimpan?'),
('remove_hint','Website data will remain on this device.','网站数据仍会保留在此设备上。','Data laman akan kekal pada peranti ini.'),
('invalid_url','Enter a valid HTTP or HTTPS URL without spaces or credentials','请输入有效的 HTTP 或 HTTPS 网址，不要包含空格或登录凭据','Masukkan URL HTTP atau HTTPS yang sah tanpa ruang atau maklumat log masuk'),
('duplicate_url','This URL is already saved','此网址已保存','URL ini sudah disimpan'),
('language','Language','语言','Bahasa'),
('language_hint','Choose the app language. Websites use their own language settings.','选择应用语言。网站使用各自的语言设置。','Pilih bahasa aplikasi. Laman web menggunakan tetapan bahasa masing-masing.'),
('saved_count','%1$d saved sites','已保存 %1$d 个网站','%1$d laman disimpan'),
('saved_one','1 saved site','已保存 1 个网站','1 laman disimpan'),
('http_error','The server returned HTTP %1$d. You can retry with Reload.','服务器返回 HTTP %1$d。你可以点击重新加载以重试。','Pelayan mengembalikan HTTP %1$d. Cuba muat semula.'),
('open_site','Open %1$s','打开 %1$s','Buka %1$s'),
('open_current','Open %1$s, current site','打开 %1$s，当前网站','Buka %1$s, laman semasa'),
('edit_site_accessible','Edit %1$s','编辑 %1$s','Edit %1$s'),
('current_page','Current page: %1$s','当前网页：%1$s','Halaman semasa: %1$s'),
]

for column, folder in [(1,'values'),(2,'values-b+zh+Hans'),(3,'values-ms')]:
    directory = root/'app/src/main/res'/folder
    directory.mkdir(parents=True, exist_ok=True)
    lines=['<?xml version="1.0" encoding="utf-8"?>','<resources>']
    for row in strings:
        value=escape(row[column]).replace("'", "\\'").replace('\n',r'\n')
        lines.append(f'    <string name="{row[0]}">{value}</string>')
    lines.append('</resources>')
    (directory/'strings.xml').write_text('\n'.join(lines)+'\n',encoding='utf-8')

source=root/'app/src/main/java/com/webshelf/app/MainActivity.java'
code=source.read_text(encoding='utf-8')
for key,en,*_ in strings:
    code=code.replace(json.dumps(en,ensure_ascii=False),f'getString(R.string.{key})')
code=code.replace('"No site selected"','getString(R.string.choose_site)')
code=code.replace('"The server returned HTTP " + response.getStatusCode() + ". You can retry with Reload."','getString(R.string.http_error, response.getStatusCode())')
code=code.replace('sites.size()+ (sites.size()==1?" saved site":" saved sites")','(sites.size()==1?getString(R.string.saved_one):getString(R.string.saved_count,sites.size()))')
code=code.replace('"Open "+url+(active?", current site":"")','getString(active?R.string.open_current:R.string.open_site,url)')
code=code.replace('"Edit "+url','getString(R.string.edit_site_accessible,url)')
code=code.replace('"Current page: "+current','getString(R.string.current_page,current)')
source.write_text(code,encoding='utf-8')
