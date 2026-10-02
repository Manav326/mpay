import { notFound } from "next/navigation";
import StorePdfReader from "../../StorePdfReader";
import { getReadableItem, readableItems } from "../../readings";

export function generateStaticParams() {
  return readableItems.map((item) => ({ slug: item.slug }));
}

export default async function ReaderPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;
  const item = getReadableItem(slug);
  if (!item) notFound();
  return <StorePdfReader item={item} />;
}
