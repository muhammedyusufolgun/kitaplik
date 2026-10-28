package com.olgun.kitaplik

import android.app.*
import android.os.*
import android.content.*
import android.graphics.*
import android.graphics.drawable.*
import android.net.Uri
import android.provider.OpenableColumns
import android.view.*
import android.widget.*
import android.text.InputType
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import android.graphics.pdf.PdfRenderer

class MainActivity : Activity() {
 data class Book(var name:String,var path:String,var cover:String="",var bookmark:Int=-1)
 private val books=mutableListOf<Book>()
 private lateinit var root:LinearLayout
 private val prefs by lazy { getSharedPreferences("books",MODE_PRIVATE) }
 private var sorting=false
 private val pickPdf=100
 private val pickCover=101
 private var coverIndex=-1
 private var current=-1
 private var page=0
 private var zoom=1f
 private var brightness=1f
 private var landscape=false
 private var renderer:PdfRenderer?=null
 private var descriptor:android.os.ParcelFileDescriptor?=null
 override fun onCreate(savedInstanceState:Bundle?) { super.onCreate(savedInstanceState); window.statusBarColor=android.graphics.Color.WHITE; window.navigationBarColor=android.graphics.Color.WHITE; window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR; load(); home() }
 private fun load(){ val a=JSONArray(prefs.getString("items","[]")); for(i in 0 until a.length()){val j=a.getJSONObject(i);books.add(Book(j.getString("name"),j.getString("path"),j.optString("cover"),j.optInt("bookmark",-1)))} }
 private fun save(){val a=JSONArray(); books.forEach { a.put(JSONObject().put("name",it.name).put("path",it.path).put("cover",it.cover).put("bookmark",it.bookmark)) };prefs.edit().putString("items",a.toString()).apply()}
 private fun text(s:String,size:Float=16f,bold:Boolean=false):TextView=TextView(this).apply{ text=s;textSize=size;setTextColor(Color.rgb(50,50,50));gravity=Gravity.CENTER;setPadding(8,10,8,10);if(bold)setTypeface(null,1)}
 private fun button(s:String,action:()->Unit)=Button(this).apply{text=s;setAllCaps(false);setOnClickListener{action()}}
 private fun init(title:String){root=LinearLayout(this).apply{orientation=1;setBackgroundColor(Color.WHITE);setPadding(8,4,8,4)};setContentView(root);val head=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL};head.addView(text(title,24f,true),LinearLayout.LayoutParams(0,-2,1f));root.addView(head)}
 private fun footer(){root.addView(text("Muhammed Yusuf OLGUN",13f,true),LinearLayout.LayoutParams(-1,-2))}
 private fun menu(anchor:View){val p=PopupMenu(this,anchor);listOf("Dosya ekle","Dosya sırala","Dosya Kapak Düzenle","Dosya sil").forEach{p.menu.add(it)};p.setOnMenuItemClickListener{when(it.title.toString()){
 "Dosya ekle"->pickPdf()
 "Dosya sırala"->{sorting=!sorting;home()}
 "Dosya Kapak Düzenle"->chooseBook("Kapak düzenle"){coverDialog(it)}
 "Dosya sil"->deleteDialog()
 };true};p.show()}
 private fun home(){current=-1;closePdf();init("Kitaplık");val bar=root.getChildAt(0) as LinearLayout;val m=button("☰ Menü"){menu(bar.getChildAt(1))};bar.addView(m)
 val sc=ScrollView(this);root.addView(sc,LinearLayout.LayoutParams(-1,0,1f))
 val stack=LinearLayout(this).apply{orientation=1};sc.addView(stack)
 val rows=maxOf(5,(books.size+4)/5)
 for(r in 0 until rows){val shelf=LinearLayout(this).apply{orientation=1;setBackgroundColor(Color.rgb(237,220,198));setPadding(4,8,4,0)}
 val row=LinearLayout(this).apply{gravity=Gravity.BOTTOM}
 for(c in 0..4){val i=r*5+c;val item=LinearLayout(this).apply{orientation=1;gravity=Gravity.CENTER}
 if(i<books.size){val image=ImageView(this).apply{scaleType=ImageView.ScaleType.CENTER_CROP;setBackgroundColor(Color.WHITE);setPadding(2,2,2,2)}
 val bmp=coverBitmap(books[i]);if(bmp!=null)image.setImageBitmap(bmp)
 else image.setImageResource(android.R.drawable.ic_menu_report_image)
 item.addView(image,LinearLayout.LayoutParams(-1,120))
 item.addView(text("OKU",12f,true))
 item.setOnClickListener{if(sorting){if(selectedMove<0){selectedMove=i;Toast.makeText(this,"Taşınacak konum için başka kitaba dokunun",Toast.LENGTH_SHORT).show()}else{val moved=books.removeAt(selectedMove);books.add(i.coerceAtMost(books.size),moved);selectedMove=-1;save();home()}}else openBook(i)}
 item.setOnLongClickListener{if(sorting){selectedMove=i;true}else false}
 }else item.addView(text(" ",12f),LinearLayout.LayoutParams(-1,152))
 row.addView(item,LinearLayout.LayoutParams(0,-2,1f))}
 shelf.addView(row);shelf.addView(View(this).apply{setBackgroundColor(Color.rgb(114,67,36));elevation=5f},LinearLayout.LayoutParams(-1,12));stack.addView(shelf)}
 if(sorting)root.addView(text("Sıralama: Taşınacak kitabı, ardından hedef kitabı seçin.",12f))
 footer()}
 private var selectedMove=-1
 private fun coverBitmap(b:Book):Bitmap?{return try{if(b.cover.isNotEmpty()&&File(b.cover).exists())BitmapFactory.decodeFile(b.cover)?.let{Bitmap.createScaledBitmap(it,160,220,true)}else{val fd=android.os.ParcelFileDescriptor.open(File(b.path),android.os.ParcelFileDescriptor.MODE_READ_ONLY);val r=PdfRenderer(fd);val p=r.openPage(0);val bmp=Bitmap.createBitmap(160,220,Bitmap.Config.ARGB_8888);bmp.eraseColor(Color.WHITE);p.render(bmp,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);p.close();r.close();fd.close();bmp}}catch(e:Exception){null}}
 private fun pickPdf(){val intent=Intent(Intent.ACTION_OPEN_DOCUMENT).apply{type="application/pdf";addCategory(Intent.CATEGORY_OPENABLE);putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true)};startActivityForResult(intent,pickPdf)}
 private fun chooseBook(title:String,action:(Int)->Unit){if(books.isEmpty()){Toast.makeText(this,"Önce PDF ekleyin",Toast.LENGTH_SHORT).show();return};AlertDialog.Builder(this).setTitle(title).setItems(books.map{it.name}.toTypedArray()){_,i->action(i)}.show()}
 private fun coverDialog(i:Int){val opts=arrayOf("Kapak seç","Yazılı kapak oluştur","İlk PDF sayfasını kullan");AlertDialog.Builder(this).setTitle(books[i].name).setItems(opts){_,n->when(n){0->{coverIndex=i;startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{type="image/*";addCategory(Intent.CATEGORY_OPENABLE)},pickCover)}
 1->{val field=EditText(this).apply{hint="Kapak yazısı";setSingleLine(false)};AlertDialog.Builder(this).setTitle("Kapak yazısı").setView(field).setPositiveButton("Oluştur"){_,_->val bmp=Bitmap.createBitmap(480,640,Bitmap.Config.ARGB_8888);val canvas=Canvas(bmp);canvas.drawColor(Color.rgb(240,232,218));val paint=Paint(3).apply{color=Color.rgb(45,55,65);textSize=38f;typeface=Typeface.DEFAULT_BOLD};field.text.toString().chunked(19).take(8).forEachIndexed{j,line->canvas.drawText(line,30f,110f+j*54,paint)};val f=File(filesDir,"cover_${System.nanoTime()}.png");FileOutputStream(f).use{bmp.compress(Bitmap.CompressFormat.PNG,100,it)};books[i].cover=f.path;save();home()}.setNegativeButton("Vazgeç",null).show()}
 else->{books[i].cover="";save();home()}}}.show()}
 private fun deleteDialog(){val checked=BooleanArray(books.size);AlertDialog.Builder(this).setTitle("Silinecek kitapları seçin").setMultiChoiceItems(books.map{it.name}.toTypedArray(),checked){_,i,v->checked[i]=v}.setPositiveButton("Sil"){_,_->if(checked.any{it})AlertDialog.Builder(this).setTitle("Emin misin?").setMessage("Seçilen kitaplar kitaplıktan silinecek.").setPositiveButton("Evet, sil"){_,_->books.indices.reversed().filter{checked[it]}.forEach{books.removeAt(it)};save();home()}.setNegativeButton("Vazgeç",null).show()}.setNegativeButton("İptal",null).show()}
 override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){super.onActivityResult(requestCode,resultCode,data);if(resultCode!=RESULT_OK||data==null)return
 if(requestCode==pickPdf){val uris=mutableListOf<Uri>();data.clipData?.let{for(i in 0 until it.itemCount)uris.add(it.getItemAt(i).uri)}?:data.data?.let{uris.add(it)}
 uris.forEach{uri->try{var name="Kitap.pdf";contentResolver.query(uri,null,null,null,null)?.use{if(it.moveToFirst()){val n=it.getColumnIndex(OpenableColumns.DISPLAY_NAME);if(n>=0)name=it.getString(n)}};val f=File(filesDir,"book_${System.nanoTime()}.pdf");contentResolver.openInputStream(uri)!!.use{input->f.outputStream().use{input.copyTo(it)}};books.add(Book(name,f.path))}catch(e:Exception){Toast.makeText(this,"Dosya eklenemedi: ${e.message}",Toast.LENGTH_LONG).show()}};save();home()
 }else if(requestCode==pickCover&&coverIndex in books.indices){try{val f=File(filesDir,"cover_${System.nanoTime()}.png");contentResolver.openInputStream(data.data!!)!!.use{input->f.outputStream().use{input.copyTo(it)}};books[coverIndex].cover=f.path;save();home()}catch(e:Exception){Toast.makeText(this,"Kapak açılamadı",Toast.LENGTH_SHORT).show()}}}
 private fun closePdf(){try{renderer?.close();descriptor?.close()}catch(_:Exception){};renderer=null;descriptor=null}
 private fun openBook(i:Int){try{closePdf();descriptor=android.os.ParcelFileDescriptor.open(File(books[i].path),android.os.ParcelFileDescriptor.MODE_READ_ONLY);renderer=PdfRenderer(descriptor!!);current=i;page=books[i].bookmark.takeIf{it>=0}?:0;page=page.coerceIn(0,renderer!!.pageCount-1);reader()}catch(e:Exception){Toast.makeText(this,"PDF açılamadı: ${e.message}",Toast.LENGTH_LONG).show();home()}}
 private fun reader(){val r=renderer?:return;init(books[current].name);val bar=root.getChildAt(0) as LinearLayout;bar.addView(button("Geri"){home()})
 val status=text("",14f,true);root.addView(status)
 val sc=ScrollView(this).apply{isFillViewport=false};root.addView(sc,LinearLayout.LayoutParams(-1,0,1f));val image=ImageView(this).apply{adjustViewBounds=true;scaleType=ImageView.ScaleType.FIT_CENTER};sc.addView(image)
 fun render(){val p=r.openPage(page);val w=(p.width*zoom).toInt().coerceIn(200,2500);val h=(p.height*zoom).toInt().coerceIn(200,3500);val bmp=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);bmp.eraseColor(Color.WHITE);p.render(bmp,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);p.close();image.setImageBitmap(bmp);status.text="${page+1} / ${r.pageCount} sayfa";sc.scrollTo(0,0)}
 val controls=LinearLayout(this).apply{gravity=Gravity.CENTER;orientation=LinearLayout.VERTICAL};root.addView(controls)
 val nav=LinearLayout(this);controls.addView(nav)
 fun addNav(s:String,f:()->Unit){nav.addView(button(s,f),LinearLayout.LayoutParams(0,-2,1f))}
 addNav("◀"){if(page>0){page--;render()}}
 addNav("▶"){if(page<r.pageCount-1){page++;render()}}
 addNav("−"){zoom=(zoom/1.25f).coerceAtLeast(.5f);render()}
 addNav("+"){zoom=(zoom*1.25f).coerceAtMost(3f);render()}
 val extras=LinearLayout(this);controls.addView(extras)
 fun addExtra(s:String,f:()->Unit){extras.addView(button(s,f),LinearLayout.LayoutParams(0,-2,1f))}
 addExtra("🔖"){books[current].bookmark=if(books[current].bookmark==page)-1 else page;save();Toast.makeText(this,if(books[current].bookmark>=0)"Ayraç aktif" else "Ayraç pasif",Toast.LENGTH_SHORT).show()}
 addExtra("☀−"){brightness=(brightness-.1f).coerceAtLeast(.15f);window.attributes=window.attributes.apply{screenBrightness=brightness}}
 addExtra("☀+"){brightness=(brightness+.1f).coerceAtMost(1f);window.attributes=window.attributes.apply{screenBrightness=brightness}}
 addExtra("↻"){landscape=!landscape;requestedOrientation=if(landscape)android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT}
 footer();render()}
 override fun onBackPressed(){if(current>=0){requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;home()}else super.onBackPressed()}
 override fun onDestroy(){closePdf();super.onDestroy()}
}
