import { NcertReader } from "../../../NcertReader";

export function generateStaticParams() {
  return [
    { id: "class-11-fundamentals-physical-geography" },
    { id: "class-11-india-physical-environment" },
    { id: "class-12-fundamentals-human-geography" },
    { id: "class-12-india-people-and-economy" },
  ];
}

export default async function NcertReadPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  return <NcertReader id={id} />;
}
