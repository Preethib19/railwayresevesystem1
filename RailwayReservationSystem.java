import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

abstract class TravelClass {
    protected String name;
    protected int capacity;
    public TravelClass(String name, int capacity) { this.name = name; this.capacity = capacity; }
    public String getName() { return name; }
    public int getCapacity() { return capacity; }
    public abstract String getDescription();
}
class SleeperClass extends TravelClass {
    public SleeperClass() { super("Sleeper", 5); }
    public String getDescription() { return "Comfortable overnight journeys"; }
}
class ACClass extends TravelClass {
    public ACClass() { super("AC", 3); }
    public String getDescription() { return "Cool, relaxed travel"; }
}
class GeneralClass extends TravelClass {
    public GeneralClass() { super("General", 10); }
    public String getDescription() { return "Simple and affordable travel"; }
}
class Train {
    int number; String name, source, destination, departure, arrival;
    Map<String, TravelClass> classes = new LinkedHashMap<>();
    Map<String, Integer> seats = new HashMap<>();
    Train(int n, String name, String from, String to, String dep, String arr) {
        number=n; this.name=name; source=from; destination=to; departure=dep; arrival=arr;
        add(new SleeperClass()); add(new ACClass()); add(new GeneralClass());
    }
    void add(TravelClass c) { classes.put(c.getName(), c); seats.put(c.getName(), c.getCapacity()); }
}
class Passenger {
    private String name, gender, phone; private int age;
    Passenger(String n,int a,String g,String p){name=n;age=a;gender=g;phone=p;}
    public String getName(){return name;} public int getAge(){return age;}
    public String getGender(){return gender;} public String getPhone(){return phone;}
}
class Booking {
    String ticket; Passenger passenger; Train train; String travelClass, status, travelDate, seatNumber;
    Booking(String t,Passenger p,Train tr,String c,String s){this(t,p,tr,c,s,LocalDate.now().toString(), "-" );}
    Booking(String t,Passenger p,Train tr,String c,String s,String date,String seat){ticket=t;passenger=p;train=tr;travelClass=c;status=s;travelDate=date;seatNumber=seat;}
}
interface ReservationOperations {
    String bookTicket(String name,int age,String gender,String phone,int trainNumber,String travelClass);
    String cancelTicket(String ticket);
}
class InvalidBookingException extends RuntimeException { InvalidBookingException(String m){super(m);} }
class ReservationService implements ReservationOperations {
    final List<Train> trains = new ArrayList<>();
    final List<Booking> bookings = new ArrayList<>();
    int nextTicket=1000;
    ReservationService(){
        trains.add(new Train(12627,"Karnataka Express","Bengaluru","Delhi","19:00","10:30"));
        trains.add(new Train(12608,"Lalbagh Express","Bengaluru","Chennai","06:30","12:00"));
        trains.add(new Train(12007,"Shatabdi Express","Chennai","Bengaluru","06:00","11:00"));
        trains.add(new Train(16591,"Hampi Express","Bengaluru","Mysuru","22:00","01:15"));
        trains.add(new Train(22691,"Rajdhani Express","Bengaluru","Mumbai","20:00","08:30"));
        load();
    }
    public synchronized String bookTicket(String name,int age,String gender,String phone,int number,String cls){ return bookTicket(name,age,gender,phone,number,cls,LocalDate.now().toString()); }
    public synchronized String bookTicket(String name,int age,String gender,String phone,int number,String cls,String date){
        if(name==null||name.isBlank()||age<1||age>120||phone==null||phone.isBlank()) throw new InvalidBookingException("Please enter valid passenger details.");
        Train t=trains.stream().filter(x->x.number==number).findFirst().orElse(null);
        if(t==null||!t.classes.containsKey(cls)) return "Please select a valid train and class.";
        int capacity=t.classes.get(cls).getCapacity();
        Set<Integer> occupied=new HashSet<>();
        for(Booking old:bookings) if(old.train.number==number && old.travelClass.equals(cls) && old.travelDate.equals(date) && old.status.equals("Confirmed")) { try { occupied.add(Integer.parseInt(old.seatNumber)); } catch(Exception ignored){} }
        int seat=0; for(int i=1;i<=capacity;i++) if(!occupied.contains(i)){seat=i;break;}
        String status=seat>0?"Confirmed":"Waiting List";
        Booking b=new Booking("RR"+(++nextTicket),new Passenger(name,age,gender,phone),t,cls,status,date,seat>0?String.valueOf(seat):"Waiting List");
        bookings.add(b); save();
        return "Your ticket "+b.ticket+" is "+status.toLowerCase(Locale.ROOT)+".";
    }
    public synchronized String cancelTicket(String ticket){
        for(Booking b:bookings) if(b.ticket.equalsIgnoreCase(ticket)&&!b.status.equals("Cancelled")){
            b.status="Cancelled"; save(); return "Ticket "+ticket+" cancelled successfully.";
        }
        return "Ticket not found, or it has already been cancelled.";
    }
    synchronized void save(){
        try(BufferedWriter w=Files.newBufferedWriter(Paths.get("bookings.csv"))){
            w.write("Ticket,Passenger,Age,Gender,Phone,Train,Class,Status,TravelDate,SeatNumber\n");
            for(Booking b:bookings) w.write(String.join(",",csv(b.ticket),csv(b.passenger.getName()),""+b.passenger.getAge(),csv(b.passenger.getGender()),csv(b.passenger.getPhone()),csv(b.train.name),csv(b.travelClass),csv(b.status),csv(b.travelDate),csv(b.seatNumber))+"\n");
        }catch(IOException e){System.err.println("Could not save bookings: "+e.getMessage());}
    }
    void load(){
        Path p=Paths.get("bookings.csv"); if(!Files.exists(p)) return;
        try(BufferedReader r=Files.newBufferedReader(p)){r.readLine();String line;while((line=r.readLine())!=null){
            String[] a=line.split(",",-1); if(a.length<8)continue;
            Train t=trains.stream().filter(x->x.name.equals(a[5])).findFirst().orElse(null); if(t==null)continue;
            Booking b=new Booking(a[0],new Passenger(a[1],Integer.parseInt(a[2]),a[3],a[4]),t,a[6],a[7],a.length>8&&!a[8].isBlank()?a[8]:LocalDate.now().toString(),a.length>9&&!a[9].isBlank()?a[9]:"-");bookings.add(b);
            try{nextTicket=Math.max(nextTicket,Integer.parseInt(a[0].replace("RR","")));}catch(Exception ignored){}

        }}catch(Exception e){System.err.println("Could not load bookings: "+e.getMessage());}
    }
    String csv(String s){return s==null?"":s.replace(","," ").replace("\n"," ");}
}
public class RailwayReservationSystem {
    static ReservationService service=new ReservationService();
    static Map<String,String> users=new ConcurrentHashMap<>(), sessions=new ConcurrentHashMap<>();
    static final String ADMIN_EMAIL="admin@railreserve.com", ADMIN_PASSWORD="Admin@123";
    static Path userFile=Paths.get("users.csv");
    public static void main(String[] args)throws IOException{
        loadUsers();
        HttpServer server=HttpServer.create(new InetSocketAddress(8080),0);
        server.setExecutor(Executors.newFixedThreadPool(10));
        server.createContext("/",RailwayReservationSystem::home);
        server.createContext("/login",RailwayReservationSystem::login);
        server.createContext("/signup",RailwayReservationSystem::signup);
        server.createContext("/logout",e->{sessions.remove(session(e));redirect(e,"/");});
        server.createContext("/book",RailwayReservationSystem::book);
        server.createContext("/cancel",RailwayReservationSystem::cancel);
        server.createContext("/history",RailwayReservationSystem::history);
        server.createContext("/admin",RailwayReservationSystem::admin);
        server.start();
        System.out.println("RailReserve is running at http://localhost:8080");
        System.out.println("Demo admin: " + ADMIN_EMAIL + " / " + ADMIN_PASSWORD);
    }
    static synchronized int available(Train t,String cls,String date){int used=0;for(Booking b:service.bookings)if(b.train.number==t.number&&b.travelClass.equals(cls)&&b.travelDate.equals(date)&&b.status.equals("Confirmed"))used++;return Math.max(0,t.classes.get(cls).getCapacity()-used);}
    static void home(HttpExchange e)throws IOException{
        String user=currentUser(e); String q=param(e,"q"); String from=param(e,"from"),to=param(e,"to");
        StringBuilder cards=new StringBuilder();
        for(Train t:service.trains){
            if(!q.isBlank()&&!t.name.toLowerCase().contains(q.toLowerCase())&&!t.source.toLowerCase().contains(q.toLowerCase())&&!t.destination.toLowerCase().contains(q.toLowerCase()))continue;
            if(!from.isBlank()&&!t.source.equalsIgnoreCase(from))continue;
            if(!to.isBlank()&&!t.destination.equalsIgnoreCase(to))continue;
            cards.append("<article class='train-card'><div class='train-icon'>🚆</div><div class='train-info'><span class='tag'>EXPRESS · ").append(t.number).append("</span><h3>").append(esc(t.name)).append("</h3><div class='route'><b>").append(esc(t.source)).append("</b><span class='line'></span><b>").append(esc(t.destination)).append("</b></div><p>").append(t.departure).append(" → ").append(t.arrival).append(" · Sleeper, AC & General</p><p><b>Seats available today:</b> General ").append(available(t,"General",LocalDate.now().toString())).append(" / ").append(t.classes.get("General").getCapacity()).append(" · Sleeper ").append(available(t,"Sleeper",LocalDate.now().toString())).append(" / ").append(t.classes.get("Sleeper").getCapacity()).append(" · AC ").append(available(t,"AC",LocalDate.now().toString())).append(" / ").append(t.classes.get("AC").getCapacity()).append("</p></div><a class='small-btn' href='#booking'>Book seat ↗</a></article>");
        }
        if(cards.length()==0)cards.append("<div class='empty'>No matching trains found. Try another route or train name.</div>");
        String nav=user.isBlank()?"<a href='/login'>Login</a><a class='nav-cta' href='/signup'>Create account</a>":"<span class='welcome'>Hi, "+esc(user)+"</span><a href='/history'>My bookings</a><a href='/logout'>Log out</a>";
        String html= """
        <!doctype html><html><head><meta charset='UTF-8'><meta name='viewport' content='width=device-width, initial-scale=1'>
        <title>RailReserve — Your journey, beautifully planned</title><style>
        :root{--navy:#071633;--blue:#2459d3;--sky:#8bb7ff;--ink:#15223b;--muted:#6e7b91;--paper:#f5f7fb}
        *{box-sizing:border-box}html{scroll-behavior:smooth}body{margin:0;background:var(--paper);color:var(--ink);font-family:Inter,Segoe UI,Arial,sans-serif}
        a{text-decoration:none;color:inherit}.nav{height:auto;min-height:78px;padding:16px clamp(18px,5vw,76px);display:flex;align-items:center;justify-content:space-between;gap:18px;background:#fff;border-bottom:1px solid #e8edf5;position:sticky;top:0;z-index:10}
        .brand{font-size:23px;font-weight:850;letter-spacing:-.8px;white-space:nowrap}.brand-mark{display:inline-grid;place-items:center;background:#e8efff;border-radius:12px;width:38px;height:38px;margin-right:8px}
        .navlinks{display:flex;align-items:center;gap:clamp(12px,2.5vw,32px);font-weight:650;color:#536079}.navlinks a:hover{color:var(--blue)}.nav-cta,.btn{background:var(--blue);color:#fff!important;border-radius:12px;padding:12px 18px;font-weight:750}
        .hero{padding:56px clamp(18px,5vw,76px) 64px;background:radial-gradient(ellipse at 85% 15%,#21478e 0,transparent 36%),linear-gradient(120deg,#07132f,#0b2455 68%,#102e68);color:#fff;overflow:hidden}
        .hero-grid{max-width:1250px;margin:auto;display:grid;grid-template-columns:1.03fr .97fr;align-items:center;gap:36px}.eyebrow{font-size:12px;font-weight:850;letter-spacing:3px;text-transform:uppercase;color:#9ec3ff;margin:0 0 20px}
        h1{font-size:clamp(42px,5.5vw,76px);line-height:1.04;letter-spacing:-2.6px;margin:0 0 22px;font-weight:900}.gradient{color:#82b0ff}.lead{font-size:clamp(17px,1.8vw,21px);line-height:1.65;color:#d5e1f8;max-width:580px;margin:0 0 28px}
        .hero-actions{display:flex;gap:12px;flex-wrap:wrap}.btn{display:inline-flex;align-items:center;justify-content:center;padding:15px 22px;border:0;cursor:pointer}.btn.light{background:#fff;color:#163366!important}.btn.ghost{border:1px solid #ffffff66;background:#ffffff0d;color:#fff!important}
        .hero-photo{height:420px;min-height:300px;position:relative;border-radius:28px;overflow:hidden;border:1px solid #ffffff36;box-shadow:0 24px 70px #0006;background:#18376c}
        .hero-photo:before{content:'';position:absolute;inset:0;background:linear-gradient(180deg,#06153200 30%,#061532d9 100%),url('https://images.unsplash.com/photo-1474487548417-781cb71495f3?auto=format&fit=crop&w=1300&q=85') center/cover}
        .photo-note{position:absolute;bottom:24px;left:24px;right:24px;padding:18px 20px;border:1px solid #ffffff50;background:#0716329c;backdrop-filter:blur(12px);border-radius:18px}.photo-note small{color:#b7d0ff;letter-spacing:1.5px;font-weight:800}.photo-note strong{display:block;font-size:22px;margin-top:6px}
        .search-wrap{max-width:1160px;margin:-28px auto 0;padding:0 22px;position:relative;z-index:2}.search-panel{background:#fff;border-radius:20px;box-shadow:0 16px 50px #10275018;padding:24px;border:1px solid #e7edf7}
        .panel-title{display:flex;justify-content:space-between;align-items:center;gap:12px;margin-bottom:18px}.panel-title h2{font-size:20px;margin:0}.panel-title span{font-size:13px;color:var(--muted)}
        .search-grid{display:grid;grid-template-columns:1fr 1fr 1fr auto;gap:13px;align-items:end}.field label{display:block;font-size:11px;letter-spacing:1px;font-weight:850;color:#6d7a90;margin:0 0 8px}.field input,.field select,.auth input{width:100%;padding:15px 14px;border:1px solid #dbe2ee;background:#f9fbff;border-radius:11px;font:inherit;outline:none;color:#24324b}.field input:focus,.field select:focus,.auth input:focus{border-color:#6591f5;box-shadow:0 0 0 3px #2459d31a}
        .section{max-width:1160px;margin:66px auto;padding:0 22px}.section-heading{display:flex;justify-content:space-between;align-items:end;gap:15px;margin-bottom:20px}.section-heading h2{font-size:clamp(27px,3vw,36px);margin:0 0 7px;letter-spacing:-1px}.section-heading p{margin:0;color:var(--muted)}
        .train-list{display:grid;gap:13px}.train-card{background:#fff;border:1px solid #e6ebf3;border-radius:18px;padding:20px;display:flex;align-items:center;gap:18px;box-shadow:0 5px 18px #10275006}.train-icon{width:56px;height:56px;flex-shrink:0;border-radius:16px;background:#eaf0ff;display:grid;place-items:center;font-size:27px}.train-info{flex:1;min-width:0}.tag{font-size:10px;font-weight:850;letter-spacing:1.3px;color:var(--blue)}.train-info h3{margin:5px 0 10px;font-size:18px}.route{display:flex;align-items:center;gap:10px;font-size:14px}.line{width:50px;height:1px;background:#9ab5ef;position:relative}.line:after{content:'✦';position:absolute;left:42%;top:-9px;background:#fff;color:#4777d7;padding:0 3px}.train-info p{margin:9px 0 0;font-size:13px;color:var(--muted)}.small-btn{padding:11px 15px;border:1px solid #d9e3f7;border-radius:10px;color:var(--blue);font-weight:750;white-space:nowrap}.empty{padding:30px;background:white;border-radius:16px;color:var(--muted)}
        .feature-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:16px}.feature{padding:24px;border-radius:18px;background:#fff;border:1px solid #e6ebf3}.feature .emoji{font-size:25px}.feature h3{margin:14px 0 8px}.feature p{margin:0;color:var(--muted);line-height:1.6;font-size:14px}
        .booking{display:grid;grid-template-columns:1fr 1fr;gap:20px}.box{background:#fff;border:1px solid #e6ebf3;border-radius:18px;padding:24px}.box h3{margin-top:0}.box form{display:grid;grid-template-columns:1fr 1fr;gap:12px}.box .field.full{grid-column:1/-1}.box button{grid-column:1/-1}
        footer{margin-top:70px;padding:32px clamp(18px,5vw,76px);background:#07132f;color:#d1def5;display:flex;justify-content:space-between;gap:15px;flex-wrap:wrap;font-size:13px}
        .auth-page{min-height:100vh;display:grid;grid-template-columns:1fr 1fr;background:#f4f7fc}.auth-art{padding:50px;background:linear-gradient(135deg,#06132fdd,#102e68cc),url('https://images.unsplash.com/photo-1474487548417-781cb71495f3?auto=format&fit=crop&w=1200&q=85') center/cover;color:white;display:flex;flex-direction:column;justify-content:space-between}.auth-art h1{font-size:clamp(38px,4vw,60px)}.auth-card-wrap{display:grid;place-items:center;padding:28px}.auth{background:#fff;border:1px solid #e6ebf3;border-radius:24px;padding:34px;width:min(100%,430px);box-shadow:0 18px 60px #10275012}.auth h2{margin:0 0 8px;font-size:30px}.auth p{color:var(--muted);line-height:1.5}.auth label{display:block;font-size:13px;font-weight:750;margin:17px 0 8px}.auth .btn{width:100%;margin-top:20px}.notice{background:#eff5ff;border:1px solid #d8e6ff;padding:12px 14px;border-radius:11px;color:#204a99;font-size:14px;margin:15px 0}.back{color:var(--blue);font-weight:750}.table-wrap{overflow:auto;background:white;border-radius:18px;border:1px solid #e6ebf3}table{width:100%;border-collapse:collapse;min-width:650px}th,td{text-align:left;padding:14px;border-bottom:1px solid #e9edf5;font-size:14px}th{background:#f3f6fc;color:#51617d}.status{font-weight:800;color:#247b52}
        @media(max-width:800px){.nav{align-items:flex-start;flex-direction:column;padding:14px 18px}.navlinks{width:100%;gap:16px;flex-wrap:wrap;font-size:13px}.brand{font-size:21px}.hero{padding-top:38px}.hero-grid{grid-template-columns:1fr;gap:28px}.hero-photo{height:280px;min-height:280px}.search-grid{grid-template-columns:1fr 1fr}.search-grid .btn{grid-column:1/-1}.feature-grid{grid-template-columns:1fr}.booking{grid-template-columns:1fr}.train-card{align-items:flex-start;flex-wrap:wrap}.train-info{min-width:calc(100% - 80px)}.small-btn{margin-left:74px}.auth-page{grid-template-columns:1fr}.auth-art{min-height:240px;padding:28px}.auth-art .auth-tagline{display:none}.section{margin:48px auto}.panel-title{align-items:flex-start;flex-direction:column}}
        @media(max-width:480px){h1{letter-spacing:-1.5px}.search-grid{grid-template-columns:1fr}.search-grid .btn{grid-column:auto}.search-panel{padding:17px}.box form{grid-template-columns:1fr}.box .field.full,.box button{grid-column:auto}.hero-photo{height:240px}.navlinks{gap:12px}.welcome{display:none}}
        </style></head><body>
        <header class='nav'><a class='brand' href='/'><span class='brand-mark'>🚆</span>RailReserve</a><nav class='navlinks'><a href='/#home'>Home</a><a href='/#trains'>Trains</a><a href='/#why'>Why RailReserve</a>__NAV__<a href='/admin'>Admin</a></nav></header>
        <main id='home'><section class='hero'><div class='hero-grid'><div><p class='eyebrow'>SMART RAILWAY RESERVATION</p><h1>Travel farther.<br><span class='gradient'>Book smarter.</span></h1><p class='lead'>Search trains, compare travel classes, reserve your seat and manage your journey — all in one calm, simple place.</p><div class='hero-actions'><a class='btn light' href='#search'>Find your train&nbsp; →</a><a class='btn ghost' href='/signup'>Create free account</a></div></div><div class='hero-photo'><div class='photo-note'><small>YOUR NEXT ADVENTURE STARTS HERE</small><strong>Every journey has a story.</strong></div></div></div></section>
        <section class='search-wrap' id='search'><div class='search-panel'><div class='panel-title'><h2>Where would you like to go?</h2><span>Plan your next journey</span></div><form action='/' method='get' class='search-grid'><div class='field'><label>FROM</label><select name='from'><option value=''>Any departure city</option><option>Bengaluru</option><option>Chennai</option></select></div><div class='field'><label>TO</label><select name='to'><option value=''>Any destination</option><option>Delhi</option><option>Chennai</option><option>Bengaluru</option><option>Mysuru</option><option>Mumbai</option></select></div><div class='field'><label>SEARCH BY TRAIN</label><input name='q' placeholder='Train name or city' value='__QUERY__'></div><button class='btn' type='submit'>Search trains →</button></form></div></section>
        <section class='section' id='trains'><div class='section-heading'><div><h2>Popular journeys</h2><p>Find a route that takes you somewhere wonderful.</p></div><span style='color:#2459d3;font-weight:800'>__COUNT__ trains</span></div><div class='train-list'>__CARDS__</div></section>
        <section class='section' id='why'><div class='section-heading'><div><h2>A smoother way to travel</h2><p>Everything you need to plan with confidence.</p></div></div><div class='feature-grid'><article class='feature'><span class='emoji'>🧭</span><h3>Simple search</h3><p>Find trains by route, train name, or destination in just a few clicks.</p></article><article class='feature'><span class='emoji'>🎟️</span><h3>Booking in one place</h3><p>Sign in to book tickets and revisit your booking history whenever you need it.</p></article><article class='feature'><span class='emoji'>🛡️</span><h3>Your account, your trips</h3><p>Create an account before booking, so your journey details stay associated with your account.</p></article></div></section>
        <section class='section' id='booking'><div class='section-heading'><div><h2>Manage your journey</h2><p>Booking and cancellation are available to signed-in users.</p></div></div><div class='booking'><div class='box'><h3>🎫 Book a ticket</h3><p style='color:#6e7b91'>__BOOKING_TEXT__</p><a class='btn' href='__BOOK_URL__'>Continue to booking →</a></div><div class='box'><h3>↩️ Cancel a ticket</h3><form action='/cancel' method='post'><div class='field full'><label>TICKET NUMBER</label><input name='ticket' placeholder='Example: RR1001' required></div><button class='btn' type='submit'>Cancel ticket</button></form></div></div></section></main>
        <footer><strong>🚆 RailReserve</strong><span>Thoughtfully designed for your next journey.</span><span>Educational demo · Not affiliated with Indian Railways</span></footer></body></html>
        """;
        html=html.replace("__NAV__",nav).replace("__CARDS__",cards.toString()).replace("__COUNT__",String.valueOf(service.trains.size())).replace("__QUERY__",esc(q)).replace("__BOOKING_TEXT__",user.isBlank()?"Please log in or create an account before booking a ticket.":"You're signed in. Continue to book your seat.").replace("__BOOK_URL__",user.isBlank()?"/login":"/book");
        send(e,html);
    }
    static void signup(HttpExchange e)throws IOException{
        if(!e.getRequestMethod().equalsIgnoreCase("POST")){send(e,authPage("Create your account","Join RailReserve and keep your journeys in one place.","/signup","Create account",false,""));return;}
        Map<String,String> p=params(e);String name=p.getOrDefault("name","").trim(),email=p.getOrDefault("email","").trim().toLowerCase(),pass=p.getOrDefault("password","");
        if(name.isBlank()||!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")||pass.length()<6){send(e,authPage("Create your account","Join RailReserve and keep your journeys in one place.","/signup","Create account",false,"Enter a valid email and a password with at least 6 characters."));return;}
        if(users.putIfAbsent(email,hash(pass))!=null){send(e,authPage("Create your account","Join RailReserve and keep your journeys in one place.","/signup","Create account",false,"An account with this email already exists. Please log in."));return;}
        saveUsers();String token=UUID.randomUUID().toString();sessions.put(token,email);cookie(e,token);redirect(e,"/");
    }
    static void login(HttpExchange e)throws IOException{
        if(!e.getRequestMethod().equalsIgnoreCase("POST")){send(e,authPage("Welcome back","Log in to book a ticket and manage your trips.","/login","Log in",true,""));return;}
        Map<String,String> p=params(e);String email=p.getOrDefault("email","").trim().toLowerCase(),pass=p.getOrDefault("password","");
        if(email.equals(ADMIN_EMAIL)&&pass.equals(ADMIN_PASSWORD)){String token=UUID.randomUUID().toString();sessions.put(token,"ADMIN");cookie(e,token);redirect(e,"/admin");return;}
        if(users.containsKey(email)&&users.get(email).equals(hash(pass))){String token=UUID.randomUUID().toString();sessions.put(token,email);cookie(e,token);redirect(e,"/");return;}
        send(e,authPage("Welcome back","Log in to book a ticket and manage your trips.","/login","Log in",true,"Email or password is incorrect."));
    }
    static String authPage(String title,String subtitle,String action,String button,boolean isLogin,String message){
        String fields=isLogin?"":"<label>FULL NAME</label><input name='name' placeholder='Your name' required maxlength='80'>";
        return "<!doctype html><html><head><meta charset='UTF-8'><meta name='viewport' content='width=device-width,initial-scale=1'><title>"+title+" · RailReserve</title>"+stylesForAuth()+"</head><body><div class='auth-page'><aside class='auth-art'><a class='brand' href='/' style='color:white'><span class='brand-mark'>🚆</span>RailReserve</a><div class='auth-tagline'><p class='eyebrow'>YOUR JOURNEY BEGINS HERE</p><h1>"+title+"</h1><p class='lead'>Travel farther. Book smarter. Make every mile memorable.</p></div><a href='/' class='back' style='color:#d5e1f8'>← Back to homepage</a></aside><main class='auth-card-wrap'><form class='auth' action='"+action+"' method='post'><h2>"+title+"</h2><p>"+subtitle+"</p>"+(message.isBlank()?"":"<div class='notice'>"+esc(message)+"</div>")+fields+"<label>EMAIL ADDRESS</label><input type='email' name='email' placeholder='you@example.com' required><label>PASSWORD</label><input type='password' name='password' placeholder='At least 6 characters' required minlength='6'><button class='btn' type='submit'>"+button+" →</button><p>"+(isLogin?"New to RailReserve? <a class='back' href='/signup'>Create an account</a>":"Already have an account? <a class='back' href='/login'>Log in</a>")+"</p>"+"</form></main></div></body></html>";
    }
    static String stylesForAuth(){return "<style>"+ "body{margin:0;font-family:Inter,Segoe UI,Arial,sans-serif;color:#15223b}.auth-page{min-height:100vh;display:grid;grid-template-columns:1fr 1fr;background:#f4f7fc}.auth-art{padding:45px;background:linear-gradient(135deg,#06132fe8,#102e68c9),url('https://images.unsplash.com/photo-1474487548417-781cb71495f3?auto=format&fit=crop&w=1200&q=85') center/cover;color:white;display:flex;flex-direction:column;justify-content:space-between}.brand{font-size:23px;font-weight:850;text-decoration:none}.brand-mark{display:inline-grid;place-items:center;background:#e8efff;border-radius:12px;width:38px;height:38px;margin-right:8px}.eyebrow{font-size:12px;font-weight:850;letter-spacing:3px;color:#9ec3ff}.auth-art h1{font-size:clamp(38px,4vw,60px);line-height:1.05}.lead{font-size:18px;line-height:1.6;color:#d5e1f8}.auth-card-wrap{display:grid;place-items:center;padding:25px}.auth{background:white;border:1px solid #e6ebf3;border-radius:24px;padding:34px;width:min(100%,430px);box-shadow:0 18px 60px #10275012}.auth h2{margin:0 0 8px;font-size:30px}.auth p{color:#6e7b91;line-height:1.5}.auth label{display:block;font-size:12px;font-weight:800;margin:17px 0 8px}.auth input{width:100%;box-sizing:border-box;padding:14px;border:1px solid #dbe2ee;background:#f9fbff;border-radius:11px;font:inherit}.btn{display:block;width:100%;box-sizing:border-box;text-align:center;background:#2459d3;color:white;border:0;border-radius:12px;padding:15px;font-weight:800;margin-top:20px;cursor:pointer}.back{color:#2459d3;font-weight:750;text-decoration:none}.notice{background:#eff5ff;border:1px solid #d8e6ff;padding:12px;border-radius:11px;color:#204a99;font-size:13px;margin:15px 0}@media(max-width:760px){.auth-page{grid-template-columns:1fr}.auth-art{min-height:220px;padding:25px}.auth-tagline{display:none}.auth-card-wrap{padding:18px}.auth{padding:26px}}"+ "</style>";}
    static void book(HttpExchange e)throws IOException{
        String user=currentUser(e);if(user.isBlank()||user.equals("ADMIN")){redirect(e,"/login");return;}
        Map<String,String> p=params(e);
        if(!e.getRequestMethod().equalsIgnoreCase("POST")){
            send(e,bookingForm("details",new HashMap<>()));return;
        }
        String stage=p.getOrDefault("stage","details");
        try{
            if(stage.equals("details")){
                validateBookingInput(p);
                send(e,bookingForm("review",p));return;
            }
            if(stage.equals("review")){
                validateBookingInput(p);
                send(e,bookingForm("payment",p));return;
            }
            if(stage.equals("payment")){
                validateBookingInput(p);
                String mode=p.getOrDefault("paymentMode","");
                if(!Arrays.asList("UPI","Card","Net Banking","Demo Pay").contains(mode)){send(e,message("Please select a payment method."));return;}
                String msg=service.bookTicket(p.getOrDefault("name",""),Integer.parseInt(p.getOrDefault("age","0")),p.getOrDefault("gender",""),p.getOrDefault("phone",""),Integer.parseInt(p.getOrDefault("train","0")),p.getOrDefault("travelClass",""),p.getOrDefault("travelDate",LocalDate.now().toString()));
                Booking b=service.bookings.get(service.bookings.size()-1);
                send(e,ticketPage(b,mode));return;
            }
            send(e,message("Please restart your booking."));
        }catch(Exception ex){send(e,message("Please check the passenger details and try again."));}
    }
    static void validateBookingInput(Map<String,String> p){
        if(p.getOrDefault("name","").isBlank()||p.getOrDefault("phone","").isBlank())throw new IllegalArgumentException();
        int age=Integer.parseInt(p.getOrDefault("age","0"));if(age<1||age>120)throw new IllegalArgumentException();
        int n=Integer.parseInt(p.getOrDefault("train","0"));String cls=p.getOrDefault("travelClass","");
        if(service.trains.stream().noneMatch(t->t.number==n)||!Arrays.asList("Sleeper","AC","General").contains(cls))throw new IllegalArgumentException();
        LocalDate date=LocalDate.parse(p.getOrDefault("travelDate",LocalDate.now().toString()));if(date.isBefore(LocalDate.now()))throw new IllegalArgumentException();
    }
    static int fare(Map<String,String> p){String cls=p.getOrDefault("travelClass","General");int base=cls.equals("AC")?1500:cls.equals("Sleeper")?650:250;int train=Integer.parseInt(p.getOrDefault("train","0"));return base+(train==12627||train==22691?500:train==16591?100:0);}
    static String hiddenBookingFields(Map<String,String> p){StringBuilder b=new StringBuilder();for(String k:Arrays.asList("name","age","gender","phone","train","travelClass","travelDate"))b.append("<input type='hidden' name='").append(k).append("' value='").append(esc(p.getOrDefault(k,""))).append("'>");return b.toString();}
    static String bookingForm(String stage,Map<String,String> p){
        String common=stylesForAuth()+"<style>body{padding:25px;background:#f5f7fb}.auth{margin:25px auto;max-width:620px}.detail{padding:12px;background:#f7f9fe;border-radius:10px;margin:8px 0}.auth select{width:100%;box-sizing:border-box;padding:14px;border:1px solid #dbe2ee;border-radius:11px;background:#f9fbff;font:inherit}</style>";
        StringBuilder h=new StringBuilder("<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'>").append(common).append("</head><body><main class='auth'><h2>🚆 RailReserve</h2>");
        if(stage.equals("details")){
            h.append("<p>Enter passenger details and choose your train. You will review the ticket before payment.</p><form method='post'><input type='hidden' name='stage' value='details'><label>PASSENGER NAME</label><input name='name' required maxlength='80'><label>AGE</label><input type='number' name='age' min='1' max='120' required><label>GENDER</label><select name='gender'><option>Female</option><option>Male</option><option>Other</option></select><label>PHONE</label><input name='phone' required><label>TRAVEL DATE</label><input type='date' name='travelDate' min='"+LocalDate.now().toString()+"' value='"+LocalDate.now().toString()+"' required><label>TRAIN</label><select name='train'>").append(trainOptions()).append("</select><label>TRAVEL CLASS</label><select name='travelClass'><option>Sleeper</option><option>AC</option><option>General</option></select><button class='btn'>Review ticket →</button></form>");
        } else {
            int price=fare(p);Train t=service.trains.stream().filter(x->x.number==Integer.parseInt(p.get("train"))).findFirst().orElse(service.trains.get(0));
            h.append("<h3>").append(stage.equals("review")?"Review your ticket":"Ticket summary & payment").append("</h3><div class='detail'><b>Passenger:</b> ").append(esc(p.get("name"))).append(" · Age ").append(esc(p.get("age"))).append(" · ").append(esc(p.get("gender"))).append("</div><div class='detail'><b>Contact:</b> ").append(esc(p.get("phone"))).append("</div><div class='detail'><b>Train:</b> ").append(esc(t.name)).append(" ( ").append(t.number).append(" )</div><div class='detail'><b>Route:</b> ").append(esc(t.source)).append(" → ").append(esc(t.destination)).append("</div><div class='detail'><b>Departure / Arrival:</b> ").append(t.departure).append(" / ").append(t.arrival).append("</div><div class='detail'><b>Travel date:</b> ").append(esc(p.getOrDefault("travelDate",LocalDate.now().toString()))).append("</div><div class='detail'><b>Available seats for this class:</b> ").append(available(t,p.getOrDefault("travelClass","General"),p.getOrDefault("travelDate",LocalDate.now().toString()))).append("</div><div class='detail'><b>Travel class:</b> ").append(esc(p.get("travelClass"))).append("</div><div class='detail'><b>Ticket amount:</b> ₹").append(price).append("</div><form method='post'>").append(hiddenBookingFields(p));
            if(stage.equals("review"))h.append("<input type='hidden' name='stage' value='review'><button class='btn'>Continue to payment →</button>");
            else h.append("<input type='hidden' name='stage' value='payment'><label>SELECT PAYMENT MODE</label><select name='paymentMode' required><option value=''>Choose payment method</option><option>UPI</option><option>Card</option><option>Net Banking</option><option>Demo Pay</option></select><p style='font-size:13px;color:#6e7b91'>This is a demonstration checkout. No real payment is processed; do not enter card or bank details.</p><button class='btn'>Pay ₹").append(price).append(" &amp; confirm booking</button>");
            h.append("</form><p><a class='back' href='/book'>Start over</a></p>");
        }
        h.append("<p><a class='back' href='/'>Home</a></p></main></body></html>");return h.toString();
    }
    static String ticketPage(Booking b,String mode){int price=b.travelClass.equals("AC")?1500:b.travelClass.equals("Sleeper")?650:250;String fareText="₹"+price;return "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'>"+stylesForAuth()+"</head><body style='padding:25px;background:#f5f7fb'><main class='auth' style='margin:30px auto;max-width:620px'><h2>✅ Booking confirmation</h2><div class='notice'>Payment simulation successful via "+esc(mode)+". No real money was charged.</div><h3>🎟️ Your ticket</h3><p><b>Ticket number:</b> "+esc(b.ticket)+"</p><p><b>Status:</b> "+esc(b.status)+"</p><p><b>Passenger:</b> "+esc(b.passenger.getName())+"</p><p><b>Age / Gender:</b> "+b.passenger.getAge()+" / "+esc(b.passenger.getGender())+"</p><p><b>Phone:</b> "+esc(b.passenger.getPhone())+"</p><p><b>Train:</b> "+esc(b.train.name)+" ("+b.train.number+")</p><p><b>Route:</b> "+esc(b.train.source)+" → "+esc(b.train.destination)+"</p><p><b>Departure / Arrival:</b> "+b.train.departure+" / "+b.train.arrival+"</p><p><b>Travel date:</b> "+esc(b.travelDate)+"</p><p><b>Class:</b> "+esc(b.travelClass)+"</p><p><b>Seat number:</b> "+esc(b.seatNumber)+"</p><p><b>Seats left in class:</b> "+available(b.train,b.travelClass,b.travelDate)+"</p><p><b>Amount paid (demo):</b> "+fareText+"</p><a class='btn' href='/history'>My bookings</a><p><a class='back' href='/'>Back to homepage</a></p></main></body></html>";}
    static String trainOptions(){StringBuilder b=new StringBuilder();for(Train t:service.trains)b.append("<option value='").append(t.number).append("'>").append(esc(t.name)).append(" — ").append(esc(t.source)).append(" to ").append(esc(t.destination)).append("</option>");return b.toString();}
    static void cancel(HttpExchange e)throws IOException{if(currentUser(e).isBlank()||currentUser(e).equals("ADMIN")){redirect(e,"/login");return;}Map<String,String> p=params(e);send(e,message(service.cancelTicket(p.getOrDefault("ticket","").trim())));}
    static void history(HttpExchange e)throws IOException{if(currentUser(e).isBlank()||currentUser(e).equals("ADMIN")){redirect(e,"/login");return;}StringBuilder rows=new StringBuilder();for(Booking b:service.bookings)rows.append("<tr><td>").append(esc(b.ticket)).append("</td><td>").append(esc(b.passenger.getName())).append("</td><td>").append(esc(b.train.name)).append("</td><td>").append(esc(b.travelClass)).append("</td><td>").append(esc(b.travelDate)).append("</td><td>").append(esc(b.seatNumber)).append("</td><td>").append(esc(b.status)).append("</td></tr>");send(e,"<html><head><meta name='viewport' content='width=device-width,initial-scale=1'>"+stylesForAuth()+"</head><body style='padding:25px;background:#f5f7fb'><a href='/'>← Home</a><h1>My bookings</h1><table style='background:white;border-collapse:collapse;width:100%'><tr><th>Ticket</th><th>Passenger</th><th>Train</th><th>Class</th><th>Travel date</th><th>Seat</th><th>Status</th></tr>"+rows+"</table></body></html>");}
    static void admin(HttpExchange e)throws IOException{if(!"ADMIN".equals(currentUser(e))){send(e,authPage("Admin portal","Sign in with your administrator account.","/login","Admin login",true,""));return;}StringBuilder rows=new StringBuilder();for(Booking b:service.bookings)rows.append("<tr><td>").append(esc(b.ticket)).append("</td><td>").append(esc(b.passenger.getName())).append("</td><td>").append(esc(b.passenger.getPhone())).append("</td><td>").append(esc(b.train.name)).append("</td><td>").append(esc(b.travelClass)).append("</td><td>").append(esc(b.status)).append("</td></tr>");send(e,"<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'><title>Admin · RailReserve</title>"+stylesForAuth()+"</head><body style='padding:28px;background:#f5f7fb'><a class='back' href='/'>← Public website</a><h1>Admin dashboard</h1><p>Booking overview · Demo mode</p><div style='overflow:auto;background:white;border-radius:16px'><table style='width:100%;border-collapse:collapse;min-width:650px'><tr><th>Ticket</th><th>Passenger</th><th>Phone</th><th>Train</th><th>Class</th><th>Travel date</th><th>Seat</th><th>Status</th></tr>"+rows+"</table></div><p><a class='back' href='/logout'>Log out</a></p></body></html>");}
    static String message(String m){return "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'>"+stylesForAuth()+"</head><body style='padding:30px;background:#f5f7fb'><main class='auth' style='margin:50px auto'><h2>🚆 RailReserve</h2><div class='notice'>"+esc(m)+"</div><a class='back' href='/'>Back to homepage</a> · <a class='back' href='/history'>My bookings</a></main></body></html>";}
    static Map<String,String> params(HttpExchange e)throws IOException{String body="";if(e.getRequestMethod().equalsIgnoreCase("POST"))body=new String(e.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);else if(e.getRequestURI().getRawQuery()!=null)body=e.getRequestURI().getRawQuery();Map<String,String> out=new HashMap<>();for(String pair:body.split("&")){if(pair.isEmpty())continue;String[] a=pair.split("=",2);out.put(URLDecoder.decode(a[0],StandardCharsets.UTF_8),a.length>1?URLDecoder.decode(a[1],StandardCharsets.UTF_8):"");}return out;}
    static String param(HttpExchange e,String key){try{return params(e).getOrDefault(key,"");}catch(Exception ex){return "";}}
    static String currentUser(HttpExchange e){String token=session(e);return sessions.getOrDefault(token,"");}
    static String session(HttpExchange e){String cookie=e.getRequestHeaders().getFirst("Cookie");if(cookie!=null)for(String c:cookie.split(";")){String[] a=c.trim().split("=",2);if(a.length==2&&a[0].equals("RRSESSION"))return a[1];}return "";}
    static void cookie(HttpExchange e,String token){e.getResponseHeaders().add("Set-Cookie","RRSESSION="+token+"; Path=/; HttpOnly; SameSite=Lax");}
    static void redirect(HttpExchange e,String path)throws IOException{e.getResponseHeaders().set("Location",path);e.sendResponseHeaders(303,-1);e.close();}
    static void send(HttpExchange e,String html)throws IOException{byte[] b=html.getBytes(StandardCharsets.UTF_8);e.getResponseHeaders().set("Content-Type","text/html; charset=UTF-8");e.getResponseHeaders().set("Cache-Control","no-store");e.sendResponseHeaders(200,b.length);try(OutputStream o=e.getResponseBody()){o.write(b);}}
    static String esc(String s){if(s==null)return "";return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");}
    static String hash(String s){try{byte[] d=MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));StringBuilder b=new StringBuilder();for(byte x:d)b.append(String.format("%02x",x));return b.toString();}catch(Exception e){throw new RuntimeException(e);}}
    static synchronized void loadUsers(){if(!Files.exists(userFile))return;try{for(String line:Files.readAllLines(userFile)){String[] a=line.split(",",2);if(a.length==2)users.put(a[0],a[1]);}}catch(IOException e){System.err.println(e.getMessage());}}
    static synchronized void saveUsers(){try{Files.write(userFile,users.entrySet().stream().map(x->x.getKey()+","+x.getValue()).toList());}catch(IOException e){System.err.println("Could not save users: "+e.getMessage());}}
}
