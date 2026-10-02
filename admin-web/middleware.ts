import { NextResponse } from "next/server";
import type { NextRequest } from "next/server";
export function middleware(request:NextRequest){
 const host=request.headers.get("host")?.split(":")[0].toLowerCase();
 const pathname=request.nextUrl.pathname;
 if(host==="store.thinkwithsujeet.in" && !pathname.startsWith("/store") && !pathname.startsWith("/_next") && !pathname.startsWith("/api") && pathname!="/favicon.ico" && !/\.[a-z0-9]+$/i.test(pathname)){
   const url=request.nextUrl.clone(); url.pathname=pathname==="/" ? "/store" : `/store${pathname}`; return NextResponse.rewrite(url);
 }
 return NextResponse.next();
}
export const config={matcher:["/:path*"]};
