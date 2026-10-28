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
 private fun init(title:String){root=LinearLayout(this).apply{orientation=1;setBackgroundColor(Color.WHITE);setPadding(8,dp(18),8,4)};setContentView(root)}
 private fun footer(){root.addView(text("Muhammed Yusuf OLGUN",13f,true),LinearLayout.LayoutParams(-1,-2))}
 private fun dp(n:Int):Int=(resources.displayMetrics.density*n).toInt()
 private fun menu(){
  val options=arrayOf("Dosya ekle","Dosya sırala","Dosya Kapak Düzenle","Dosya sil")
  AlertDialog.Builder(this).setTitle("Kitaplık Menüsü").setItems(options){_,which->
   when(which){
    0->pickPdf()
    1->{sorting=!sorting;selectedMove=-1;home()}
    2->chooseBook("Kapak düzenle"){coverDialog(it)}
    3->deleteDialog()
   }
  }.setNegativeButton("Kapat",null).show()
 }
 private fun wood(color:Int):GradientDrawable=GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
  intArrayOf(Color.rgb(247,222,181),color,Color.rgb(102,59,32))).apply{cornerRadius=dp(5).toFloat()}
 private fun home(){
  current=-1;closePdf();init("Kitaplık")
  val header=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL;setPadding(dp(8),0,dp(8),0)}
  header.addView(text("Kitaplık",24f,true).apply{gravity=Gravity.CENTER_VERTICAL or Gravity.START},LinearLayout.LayoutParams(0,dp(60),1f))
  val menuButton=button("☰ Menü"){menu()}
  menuButton.textSize=14f
  header.addView(menuButton,LinearLayout.LayoutParams(dp(128),dp(52)))
  root.addView(header,LinearLayout.LayoutParams(-1,dp(68)))
  val sc=ScrollView(this).apply{clipToPadding=false;setPadding(dp(5),dp(10),dp(5),dp(12))}
  root.addView(sc,LinearLayout.LayoutParams(-1,0,1f))
  val stack=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(Color.WHITE)}
  sc.addView(stack)
  val rows=maxOf(4,(books.size+3)/4)
  for(r in 0 until rows){
   val shelf=LinearLayout(this).apply{
    orientation=LinearLayout.VERTICAL
    background=GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
      intArrayOf(Color.rgb(119,72,39),Color.rgb(230,186,126),Color.rgb(122,75,42))).apply{cornerRadius=dp(9).toFloat()}
    setPadding(dp(7),dp(12),dp(7),dp(5))
    elevation=dp(3).toFloat()
   }
   val row=LinearLayout(this).apply{gravity=Gravity.BOTTOM;setPadding(dp(3),dp(4),dp(3),dp(2))}
   for(col in 0..3){
    val i=r*4+col
    val slot=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL;setPadding(dp(2),dp(2),dp(2),dp(3))}
    if(i<books.size){
     val cover=ImageView(this).apply{
      scaleType=ImageView.ScaleType.FIT_CENTER
      setBackgroundColor(Color.rgb(255,253,249))
      elevation=dp(2).toFloat()
      coverBitmap(books[i])?.let{setImageBitmap(it)}?:setImageResource(android.R.drawable.ic_menu_report_image)
     }
     slot.addView(cover,LinearLayout.LayoutParams(-1,dp(90)))
     slot.addView(text("OKU",11f,true).apply{setPadding(0,dp(2),0,0)})
     slot.setOnClickListener{
      if(sorting){
       if(selectedMove<0){selectedMove=i;Toast.makeText(this,"Şimdi hedef kitaba dokunun",Toast.LENGTH_SHORT).show()}
       else{val moved=books.removeAt(selectedMove);books.add(i.coerceAtMost(books.size),moved);selectedMove=-1;save();home()}
      }else openBook(i)
     }
     if(sorting)slot.setOnLongClickListener{
      val clip=ClipData.newPlainText("book",i.toString())
      slot.startDragAndDrop(clip,View.DragShadowBuilder(slot),null,0);true
     }
     if(sorting)slot.setOnDragListener{_,event->
      if(event.action==DragEvent.ACTION_DROP){
       val source=event.clipData.getItemAt(0).text.toString().toIntOrNull()
       if(source!=null&&source in books.indices){val moved=books.removeAt(source);val target=if(source<i)i-1 else i;books.add(target.coerceIn(0,books.size),moved);save();home()}
       true
      }else true
     }
    }else{
     slot.addView(View(this),LinearLayout.LayoutParams(-1,dp(111)))
     if(sorting)slot.setOnDragListener{_,event->
      if(event.action==DragEvent.ACTION_DROP){val source=event.clipData.getItemAt(0).text.toString().toIntOrNull();if(source!=null&&source in books.indices){val moved=books.removeAt(source);books.add(i.coerceAtMost(books.size),moved);save();home()};true}else true
     }
    }
    row.addView(slot,LinearLayout.LayoutParams(0,dp(120),1f))
   }
   shelf.addView(row)
   val top=View(this).apply{background=wood(Color.rgb(173,111,59))}
   shelf.addView(top,LinearLayout.LayoutParams(-1,dp(15)))
   val edge=View(this).apply{background=GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
    intArrayOf(Color.rgb(103,57,28),Color.rgb(76,40,23)))}
   shelf.addView(edge,LinearLayout.LayoutParams(-1,dp(7)))
   val lp=LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(18)}
   stack.addView(shelf,lp)
  }
  if(sorting)root.addView(text("Sıralama açık: Kitaba uzun basıp sürükleyin.",12f))
  footer()
 }
 private var selectedMove=-1
 private fun coverBitmap(b:Book):Bitmap?{return try{if(b.cover.isNotEmpty()&&File(b.cover).exists())BitmapFactory.decodeFile(b.cover)?.let{Bitmap.createScaledBitmap(it,160,220,true)}else{val fd=android.os.ParcelFileDescriptor.open(File(b.path),android.os.ParcelFileDescriptor.MODE_READ_ONLY);val r=PdfRenderer(fd);val p=r.openPage(0);val bmp=Bitmap.createBitmap(160,220,Bitmap.Config.ARGB_8888);bmp.eraseColor(Color.WHITE);p.render(bmp,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);p.close();r.close();fd.close();bmp}}catch(e:Exception){null}}
 private fun pickPdf(){val intent=Intent(Intent.ACTION_OPEN_DOCUMENT).apply{type="application/pdf";addCategory(Intent.CATEGORY_OPENABLE);putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true)};startActivityForResult(intent,pickPdf)}
 private fun chooseBook(title:String,action:(Int)->Unit){if(books.isEmpty()){Toast.makeText(this,"Önce PDF ekleyin",Toast.LENGTH_SHORT).show();return};AlertDialog.Builder(this).setTitle(title).setItems(books.map{it.name}.toTypedArray()){_,i->action(i)}.show()}
 private fun coverDialog(i:Int){
  AlertDialog.Builder(this).setTitle(books[i].name).setItems(arrayOf("Kapak seç","İlk PDF sayfasını kullan")){_,choice->
   if(choice==0){coverIndex=i;startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{type="image/*";addCategory(Intent.CATEGORY_OPENABLE)},pickCover)}
   else{books[i].cover="";save();home()}
  }.show()
 }
 private fun deleteDialog(){val checked=BooleanArray(books.size);AlertDialog.Builder(this).setTitle("Silinecek kitapları seçin").setMultiChoiceItems(books.map{it.name}.toTypedArray(),checked){_,i,v->checked[i]=v}.setPositiveButton("Sil"){_,_->if(checked.any{it})AlertDialog.Builder(this).setTitle("Emin misin?").setMessage("Seçilen kitaplar kitaplıktan silinecek.").setPositiveButton("Evet, sil"){_,_->books.indices.reversed().filter{checked[it]}.forEach{books.removeAt(it)};save();home()}.setNegativeButton("Vazgeç",null).show()}.setNegativeButton("İptal",null).show()}
 override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){super.onActivityResult(requestCode,resultCode,data);if(resultCode!=RESULT_OK||data==null)return
 if(requestCode==pickPdf){val uris=mutableListOf<Uri>();data.clipData?.let{for(i in 0 until it.itemCount)uris.add(it.getItemAt(i).uri)}?:data.data?.let{uris.add(it)}
 uris.forEach{uri->try{var name="Kitap.pdf";contentResolver.query(uri,null,null,null,null)?.use{if(it.moveToFirst()){val n=it.getColumnIndex(OpenableColumns.DISPLAY_NAME);if(n>=0)name=it.getString(n)}};val f=File(filesDir,"book_${System.nanoTime()}.pdf");contentResolver.openInputStream(uri)!!.use{input->f.outputStream().use{input.copyTo(it)}};books.add(Book(name,f.path))}catch(e:Exception){Toast.makeText(this,"Dosya eklenemedi: ${e.message}",Toast.LENGTH_LONG).show()}};save();home()
 }else if(requestCode==pickCover&&coverIndex in books.indices){try{val f=File(filesDir,"cover_${System.nanoTime()}.png");contentResolver.openInputStream(data.data!!)!!.use{input->f.outputStream().use{input.copyTo(it)}};books[coverIndex].cover=f.path;save();home()}catch(e:Exception){Toast.makeText(this,"Kapak açılamadı",Toast.LENGTH_SHORT).show()}}}
 private fun closePdf(){try{renderer?.close();descriptor?.close()}catch(_:Exception){};renderer=null;descriptor=null}
 private fun openBook(i:Int){try{closePdf();descriptor=android.os.ParcelFileDescriptor.open(File(books[i].path),android.os.ParcelFileDescriptor.MODE_READ_ONLY);renderer=PdfRenderer(descriptor!!);current=i;page=books[i].bookmark.takeIf{it>=0}?:0;page=page.coerceIn(0,renderer!!.pageCount-1);reader()}catch(e:Exception){Toast.makeText(this,"PDF açılamadı: ${e.message}",Toast.LENGTH_LONG).show();home()}}
 private fun reader(){val r=renderer?:return;init(books[current].name);val bar=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL};bar.addView(text(books[current].name,18f,true),LinearLayout.LayoutParams(0,dp(56),1f));bar.addView(button("Geri"){home()});root.addView(bar)
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
