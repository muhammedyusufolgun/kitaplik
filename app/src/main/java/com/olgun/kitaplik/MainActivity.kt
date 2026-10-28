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
 private var editMode=false
 private val modeHandler=Handler(Looper.getMainLooper())
 private var modeTimeout:Runnable?=null
 private fun cancelModeTimeout(){modeTimeout?.let{modeHandler.removeCallbacks(it)};modeTimeout=null}
 private fun scheduleModeTimeout(){cancelModeTimeout();if(sorting||editMode||deleteMode){val task=Runnable{if(current<0&&(sorting||editMode||deleteMode)){sorting=false;editMode=false;deleteMode=false;selectedDelete.clear();selectedMove=-1;home()}};modeTimeout=task;modeHandler.postDelayed(task,5000)}}
 private fun exitEditMode(){if(sorting||editMode||deleteMode){sorting=false;editMode=false;deleteMode=false;selectedDelete.clear();selectedMove=-1;cancelModeTimeout();home()}}
 private val selectedDelete=mutableSetOf<Int>()
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
 private fun init(title:String){root=LinearLayout(this).apply{orientation=1;setBackgroundColor(Color.WHITE);setPadding(8,dp(26),8,4)};setContentView(root)}
 private fun footer(){root.addView(text("Muhammed Yusuf OLGUN",13f,true),LinearLayout.LayoutParams(-1,-2))}
 private fun dp(n:Int):Int=(resources.displayMetrics.density*n).toInt()
 private fun menu(){
  val options=arrayOf("Dosya ekle","Dosya sırala","Dosya sil")
  cancelModeTimeout()
  AlertDialog.Builder(this).setTitle("Kitaplık Menüsü").setItems(options){_,which->
   when(which){
    0->{sorting=false;editMode=false;deleteMode=false;selectedDelete.clear();pickPdf()}
    1->{sorting=!sorting;editMode=false;selectedMove=-1;home()}
    2->{editMode=false;sorting=false;deleteMode=true;selectedDelete.clear();home()}
   }
  }.setNegativeButton("Kapat",null).show()
 }
 private fun wood(color:Int):GradientDrawable=GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
  intArrayOf(Color.rgb(247,222,181),color,Color.rgb(102,59,32))).apply{cornerRadius=dp(5).toFloat()}
 private fun home(){
  current=-1;closePdf();init("Kitaplık")
  val header=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL;setPadding(dp(8),0,dp(8),0)}
  header.addView(text("Kitaplık",24f,true).apply{gravity=Gravity.CENTER_VERTICAL or Gravity.START;setOnClickListener{exitEditMode()}},LinearLayout.LayoutParams(0,dp(60),1f))
  val menuButton=button("☰ Menü"){menu()}
  menuButton.textSize=14f
  header.addView(menuButton,LinearLayout.LayoutParams(dp(128),dp(52)))
  root.addView(header,LinearLayout.LayoutParams(-1,dp(56)))
  val sc=ScrollView(this).apply{clipToPadding=false;setPadding(dp(5),dp(1),dp(5),0)}
  root.addView(sc,LinearLayout.LayoutParams(-1,0,1f))
  val stack=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(Color.WHITE);setOnClickListener{exitEditMode()}}
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
   shelf.setOnClickListener{exitEditMode()}
   val row=LinearLayout(this).apply{gravity=Gravity.BOTTOM;setPadding(dp(3),dp(4),dp(3),dp(2));setOnClickListener{exitEditMode()}}
   for(col in 0..3){
    val i=r*4+col
    val slot=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL;setPadding(dp(2),dp(2),dp(2),dp(3))}
    if(i<books.size && books[i].path.isNotEmpty()){
     val cover=ImageView(this).apply{
      scaleType=ImageView.ScaleType.FIT_CENTER
      setBackgroundColor(Color.rgb(255,253,249))
      elevation=dp(2).toFloat()
      coverBitmap(books[i])?.let{setImageBitmap(it)}?:setImageResource(android.R.drawable.ic_menu_report_image)
     }
     slot.addView(cover,LinearLayout.LayoutParams(-1,dp(111)))

     slot.setOnClickListener{
      if(editMode){cancelModeTimeout();editMode=false;coverDialog(i)}else if(deleteMode){if(!selectedDelete.add(i))selectedDelete.remove(i);slot.alpha=if(i in selectedDelete)0.45f else 1f;scheduleModeTimeout()}else if(sorting){
       if(selectedMove<0){selectedMove=i;scheduleModeTimeout();Toast.makeText(this,"Şimdi hedef kitaba dokunun",Toast.LENGTH_SHORT).show()}
       else{moveBook(selectedMove,i);selectedMove=-1;home()}
      }else openBook(i)
     }
     if(deleteMode&&i in selectedDelete)slot.alpha=0.45f
     if(sorting)slot.setOnLongClickListener{
      val clip=ClipData.newPlainText("book",i.toString())
      cancelModeTimeout();slot.startDragAndDrop(clip,View.DragShadowBuilder(slot),null,0);true
     }
     if(sorting)slot.setOnDragListener{_,event->
      if(event.action==DragEvent.ACTION_DROP){
       val source=event.clipData.getItemAt(0).text.toString().toIntOrNull()
       if(source!=null&&source in books.indices && books[source].path.isNotEmpty()){moveBook(source,i);home()}
       true
      }else true
     }
    }else{
     slot.addView(View(this).apply{setOnClickListener{exitEditMode()}},LinearLayout.LayoutParams(-1,dp(111)))
     slot.setOnClickListener{exitEditMode()}
     if(sorting)slot.setOnDragListener{_,event->
      if(event.action==DragEvent.ACTION_DROP){val source=event.clipData.getItemAt(0).text.toString().toIntOrNull();if(source!=null&&source in books.indices && books[source].path.isNotEmpty()){moveBook(source,i);home()};true}else true
     }
    }
    row.addView(slot,LinearLayout.LayoutParams(0,dp(125),1f))
   }
   shelf.addView(row)
   val top=View(this).apply{background=wood(Color.rgb(173,111,59))}
   top.setOnClickListener{exitEditMode()}
   shelf.addView(top,LinearLayout.LayoutParams(-1,dp(15)))
   val edge=View(this).apply{background=GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
    intArrayOf(Color.rgb(103,57,28),Color.rgb(76,40,23)))}
   edge.setOnClickListener{exitEditMode()}
   shelf.addView(edge,LinearLayout.LayoutParams(-1,dp(7)))
   val lp=LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=if(r==rows-1)dp(1) else dp(18)}
   stack.addView(shelf,lp)
  }
  stack.addView(text("Muhammed Yusuf OLGUN",13f,true).apply{setTextColor(Color.rgb(170,170,170));setPadding(dp(8),0,dp(8),0);gravity=Gravity.CENTER},LinearLayout.LayoutParams(-1,dp(26)))
  if(sorting)root.addView(text("Sıralama: Kitaba uzun basıp hedef konuma sürükleyin.",12f))
  if(editMode)root.addView(button("Kapak düzenlemeyi bitir"){editMode=false;home()})
  if(deleteMode){
   val actions=LinearLayout(this).apply{setPadding(dp(8),0,dp(8),dp(12))}
   actions.addView(button("Seçilenleri Sil"){
    if(selectedDelete.isEmpty()){Toast.makeText(this,"Silinecek kitap seçin",Toast.LENGTH_SHORT).show()}
    else{
     cancelModeTimeout()
     AlertDialog.Builder(this).setTitle("Sil").setMessage("${selectedDelete.size} kitap kitaplıktan silinsin mi?")
      .setPositiveButton("Devam"){_,_->
       AlertDialog.Builder(this).setTitle("Emin misin?").setMessage("Seçilen kitaplar kitaplıktan kaldırılacak.")
        .setPositiveButton("Evet, sil"){_,_->
         selectedDelete.filter{it in books.indices && books[it].path.isNotEmpty()}.sortedDescending().forEach{books[it]=Book("","")}
         selectedDelete.clear();deleteMode=false;save();home()
        }.setNegativeButton("Vazgeç"){_,_->scheduleModeTimeout()}.show()
      }.setNegativeButton("İptal"){_,_->scheduleModeTimeout()}.show()
    }
   },LinearLayout.LayoutParams(0,-2,1f))
   actions.addView(button("İptal"){deleteMode=false;selectedDelete.clear();home()},LinearLayout.LayoutParams(0,-2,1f))
   root.addView(actions,LinearLayout.LayoutParams(-1,dp(84)))
  }

  scheduleModeTimeout()
 }
 private fun moveBook(source:Int,target:Int){
  if(source !in books.indices || books[source].path.isEmpty() || target<0)return
  val moved=books[source]
  if(target>=books.size){while(books.size<=target)books.add(Book("",""));books[target]=moved;books[source]=Book("","")}
  else if(books[target].path.isEmpty()){books[target]=moved;books[source]=Book("","")}
  else if(source!=target){books.removeAt(source);books.add((if(source<target)target-1 else target).coerceIn(0,books.size),moved)}
  save()
 }
 private var selectedMove=-1
 private var deleteMode=false
 private fun coverBitmap(b:Book):Bitmap?{return try{if(b.cover.isNotEmpty()&&File(b.cover).exists())BitmapFactory.decodeFile(b.cover)?.let{Bitmap.createScaledBitmap(it,160,220,true)}else{val fd=android.os.ParcelFileDescriptor.open(File(b.path),android.os.ParcelFileDescriptor.MODE_READ_ONLY);val r=PdfRenderer(fd);val p=r.openPage(0);val bmp=Bitmap.createBitmap(160,220,Bitmap.Config.ARGB_8888);bmp.eraseColor(Color.WHITE);p.render(bmp,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);p.close();r.close();fd.close();bmp}}catch(e:Exception){null}}
 private fun pickPdf(){val intent=Intent(Intent.ACTION_OPEN_DOCUMENT).apply{type="application/pdf";addCategory(Intent.CATEGORY_OPENABLE);putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true)};startActivityForResult(intent,pickPdf)}
 private fun chooseBook(title:String,action:(Int)->Unit){if(books.isEmpty()){Toast.makeText(this,"Önce PDF ekleyin",Toast.LENGTH_SHORT).show();return};AlertDialog.Builder(this).setTitle(title).setItems(books.filter{it.path.isNotEmpty()}.map{it.name}.toTypedArray()){_,i->action(i)}.show()}
 private fun coverDialog(i:Int){
  AlertDialog.Builder(this).setTitle(books[i].name).setItems(arrayOf("Kapak seç","İlk PDF sayfasını kullan")){_,choice->
   if(choice==0){coverIndex=i;startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{type="image/*";addCategory(Intent.CATEGORY_OPENABLE)},pickCover)}
   else{books[i].cover="";save();home()}
  }.show()
 }
 private fun deleteDialog(){val checked=BooleanArray(books.size);AlertDialog.Builder(this).setTitle("Silinecek kitapları seçin").setMultiChoiceItems(books.map{it.name}.toTypedArray(),checked){_,i,v->checked[i]=v}.setPositiveButton("Sil"){_,_->if(checked.any{it})AlertDialog.Builder(this).setTitle("Emin misin?").setMessage("Seçilen kitaplar kitaplıktan silinecek.").setPositiveButton("Evet, sil"){_,_->books.indices.reversed().filter{checked[it]}.forEach{books.removeAt(it)};save();home()}.setNegativeButton("Vazgeç",null).show()}.setNegativeButton("İptal",null).show()}
 override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){super.onActivityResult(requestCode,resultCode,data);if(resultCode!=RESULT_OK||data==null)return
 if(requestCode==pickPdf){val uris=mutableListOf<Uri>();data.clipData?.let{for(i in 0 until it.itemCount)uris.add(it.getItemAt(i).uri)}?:data.data?.let{uris.add(it)}
 uris.forEach{uri->try{var name="Kitap.pdf";contentResolver.query(uri,null,null,null,null)?.use{if(it.moveToFirst()){val n=it.getColumnIndex(OpenableColumns.DISPLAY_NAME);if(n>=0)name=it.getString(n)}};val f=File(filesDir,"book_${System.nanoTime()}.pdf");contentResolver.openInputStream(uri)!!.use{input->f.outputStream().use{input.copyTo(it)}};val empty=books.indexOfFirst{it.path.isEmpty()};if(empty>=0)books[empty]=Book(name,f.path) else books.add(Book(name,f.path))}catch(e:Exception){Toast.makeText(this,"Dosya eklenemedi: ${e.message}",Toast.LENGTH_LONG).show()}};save();home()
 }else if(requestCode==pickCover&&coverIndex in books.indices){try{val f=File(filesDir,"cover_${System.nanoTime()}.png");contentResolver.openInputStream(data.data!!)!!.use{input->f.outputStream().use{input.copyTo(it)}};books[coverIndex].cover=f.path;save();home()}catch(e:Exception){Toast.makeText(this,"Kapak açılamadı",Toast.LENGTH_SHORT).show()}}}
 private fun closePdf(){try{renderer?.close();descriptor?.close()}catch(_:Exception){};renderer=null;descriptor=null}
 private fun openBook(i:Int){try{closePdf();descriptor=android.os.ParcelFileDescriptor.open(File(books[i].path),android.os.ParcelFileDescriptor.MODE_READ_ONLY);renderer=PdfRenderer(descriptor!!);current=i;page=books[i].bookmark.takeIf{it>=0}?:0;page=page.coerceIn(0,renderer!!.pageCount-1);zoom=1f;reader()}catch(e:Exception){Toast.makeText(this,"PDF açılamadı: ${e.message}",Toast.LENGTH_LONG).show();home()}}
 private fun reader(){
  val r=renderer?:return
  init(books[current].name)
  val isLandscape=resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE
  if(isLandscape)root.setPadding(0,0,0,0)
  zoom=zoom.coerceIn(0.5f,if(isLandscape)5f else 3f)
  val status=text("",12f,true).apply{setPadding(2,0,2,0)}
  if(!isLandscape)root.addView(View(this),LinearLayout.LayoutParams(-1,dp(20)))
  root.addView(status,LinearLayout.LayoutParams(-1,dp(28)))
  val controls=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
  val zoomLabel=text("",14f,true).apply{setPadding(dp(4),0,dp(4),0)}
  controls.addView(zoomLabel,LinearLayout.LayoutParams(dp(72),dp(36)))
  val bookmarkButton=text("",14f,true).apply{setPadding(2,0,2,0)}
  controls.addView(bookmarkButton,LinearLayout.LayoutParams(0,dp(36),1f))
  root.addView(controls,LinearLayout.LayoutParams(-1,dp(36)))
  fun refreshBookmark(){
   bookmarkButton.text=if(books[current].bookmark==page)"Sayfa Ayıracını Kaldır" else "Sayfa Ayıracı Ekle"
   status.text="${page+1} / ${r.pageCount}    ${if(books[current].bookmark==page)"🔖" else ""}"
  }
  fun toggleBookmark(){
   books[current].bookmark=if(books[current].bookmark==page)-1 else page
   save();refreshBookmark()
   Toast.makeText(this,if(books[current].bookmark==page)"Sayfa imi eklendi" else "Sayfa imi kaldırıldı",Toast.LENGTH_SHORT).show()
  }
  bookmarkButton.setOnClickListener{toggleBookmark()}
  val horizontal=HorizontalScrollView(this).apply{isHorizontalScrollBarEnabled=false;isFillViewport=true}
  val scroll=ScrollView(this).apply{isVerticalScrollBarEnabled=false;isFillViewport=true}
  val column=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(Color.WHITE)}
  horizontal.addView(column,android.widget.FrameLayout.LayoutParams(-2,-2))
  scroll.addView(horizontal,android.widget.FrameLayout.LayoutParams(-1,-2))
  root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
  val images=ArrayList<ImageView>()
  val pageHeights=ArrayList<Int>()
  val pageWidths=ArrayList<Int>()
  val screenWidth=resources.displayMetrics.widthPixels
  for(i in 0 until r.pageCount){
   val p=r.openPage(i)
   val pw=p.width.coerceAtLeast(1);val ph=p.height.coerceAtLeast(1);p.close()
   val w=screenWidth.coerceAtLeast(1)
   val h=(w.toFloat()*ph/pw).toInt().coerceAtLeast(1)
   pageWidths.add(w);pageHeights.add(h)
   val img=ImageView(this).apply{scaleType=ImageView.ScaleType.FIT_XY;setBackgroundColor(Color.WHITE)}
   images.add(img)
   column.addView(img,LinearLayout.LayoutParams(w,h).apply{bottomMargin=dp(3)})
  }
  fun renderPage(i:Int){
   if(i !in images.indices || images[i].drawable!=null)return
   try{
    val p=r.openPage(i)
    try{
     val w=pageWidths[i].coerceIn(200,1200)
     val h=pageHeights[i].coerceIn(200,1800)
     val bmp=Bitmap.createBitmap(w,h,Bitmap.Config.RGB_565)
     bmp.eraseColor(Color.WHITE)
     p.render(bmp,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
     images[i].setImageBitmap(bmp)
    }finally{p.close()}
   }catch(e:Exception){android.util.Log.e("Kitaplik","PDF sayfa görüntüsü oluşturulamadı: $i",e)}
  }
  renderPage(page.coerceIn(0,r.pageCount-1))
  if(page!=0)renderPage(0)
  var rendering=false
  fun updateVisible(){
   if(rendering||scroll.height<=0)return
   rendering=true
   try{
    val top=scroll.scrollY
    val bottom=top+scroll.height
    var y=0
    var active=page
    var bestDistance=Int.MAX_VALUE
    for(i in images.indices){
     val h=images[i].layoutParams.height+dp(3)
     val center=y+h/2
     val distance=kotlin.math.abs(center-(top+scroll.height/2))
     if(distance<bestDistance){bestDistance=distance;active=i}
     val visible=y+h>=top-scroll.height && y<=bottom+scroll.height
     if(visible)renderPage(i)
     else if(images[i].drawable!=null)images[i].setImageDrawable(null)
     y+=h
    }
    if(page!=active){page=active;refreshBookmark()}
   }finally{rendering=false}
  }
  fun applyZoom(){
   zoomLabel.text="${(zoom*100).toInt()}%"
   for(i in images.indices){
    val lp=images[i].layoutParams as LinearLayout.LayoutParams
    lp.width=(pageWidths[i]*zoom).toInt().coerceAtLeast(1)
    lp.height=(pageHeights[i]*zoom).toInt().coerceAtLeast(1)
    images[i].layoutParams=lp
    val old=images[i].drawable
    images[i].setImageDrawable(null)
    // Drawable is detached; Android manages bitmap lifetime.
   }
   column.requestLayout()
   scroll.post{renderPage(page);updateVisible()}
  }
  scroll.setOnScrollChangeListener{_,_,_,_,_->updateVisible()}
  scroll.addOnLayoutChangeListener{_,_,_,_,_,_,_,_,_->updateVisible()}
  val scale=android.view.ScaleGestureDetector(this,object:android.view.ScaleGestureDetector.SimpleOnScaleGestureListener(){
   override fun onScale(detector:android.view.ScaleGestureDetector):Boolean{
    val next=(zoom*detector.scaleFactor).coerceIn(0.5f,if(isLandscape)5f else 3f)
    if(kotlin.math.abs(next-zoom)>0.01f){zoom=next;applyZoom()}
    return true
   }
  })
  var downX=0f;var downY=0f;var lastY=0f;var startBrightness=brightness
  var taps=0;var lastTap=0L;var multiTouch=false
  scroll.setOnTouchListener{_,event->
   scale.onTouchEvent(event)
   when(event.actionMasked){
    MotionEvent.ACTION_DOWN->{downX=event.x;downY=event.y;lastY=event.y;startBrightness=brightness;multiTouch=false}
    MotionEvent.ACTION_POINTER_DOWN->{multiTouch=true}
    MotionEvent.ACTION_MOVE->{
     if(!multiTouch&&downX<dp(65)&&kotlin.math.abs(event.y-downY)>dp(8)){
      brightness=(startBrightness+(downY-event.y)/dp(200).toFloat()).coerceIn(0.05f,1f)
      window.attributes=window.attributes.apply{screenBrightness=brightness}
      return@setOnTouchListener true
     }
    }
    MotionEvent.ACTION_UP->{
     if(!multiTouch&&kotlin.math.abs(event.y-downY)<dp(20)&&kotlin.math.abs(event.x-downX)<dp(20)){
      val now=android.os.SystemClock.uptimeMillis()
      taps=if(now-lastTap<450)taps+1 else 1;lastTap=now
      if(taps>=3){toggleBookmark();taps=0}
     }
    }
   }
   multiTouch||scale.isInProgress
  }
  refreshBookmark()
  zoomLabel.text="${(zoom*100).toInt()}%"
  scroll.post{
   var target=0
   for(i in 0 until page.coerceIn(0,r.pageCount-1))target+=pageHeights[i]+dp(3)
   scroll.scrollTo(0,(target*zoom).toInt())
   updateVisible()
  }
 }
 override fun onConfigurationChanged(newConfig:android.content.res.Configuration){
  super.onConfigurationChanged(newConfig)
  if(current>=0)reader() else home()
 }
 override fun onBackPressed(){if(current>=0){requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;home()}else super.onBackPressed()}
 override fun onDestroy(){cancelModeTimeout();closePdf();super.onDestroy()}
}
