import { notFound } from "next/navigation";
import NcertPdfReader from "../../../NcertPdfReader";
import { ncertStudyBooksBySlug } from "../../../ncertStudyBooks";

export function generateStaticParams(){
  return Object.keys(ncertStudyBooksBySlug).map((slug)=>({slug}));
}

export default async function NcertStudyPage({params}:{params:Promise<{slug:string}>}){
  const {slug}=await params;
  if(!ncertStudyBooksBySlug[slug]) notFound();
  return <NcertPdfReader id={slug}/>;
}
