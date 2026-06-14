"""Document loading for the LlamaIndex RAG pipeline."""

from __future__ import annotations

import logging
import json
from pathlib import Path
from typing import Iterable
from urllib.parse import quote
import zipfile

from llama_index.core import Document

from deeptutor.services.rag.file_routing import FileTypeRouter
from deeptutor.utils.document_extractor import DocumentExtractionError, extract_text_from_path
from deeptutor.utils.document_validator import DocumentValidator


class LlamaIndexDocumentLoader:
    """Convert source files into LlamaIndex ``Document`` objects."""

    def __init__(self, logger=None) -> None:
        self.logger = logger or logging.getLogger(__name__)

    async def load(self, file_paths: Iterable[str]) -> list[Document]:
        documents: list[Document] = []
        classification = FileTypeRouter.classify_files(list(file_paths))

        for file_path_str in classification.parser_files:
            file_path = Path(file_path_str)
            self.logger.info(f"Parsing document: {file_path.name}")
            text = self._extract_parser_text(file_path)
            image_refs = self._extract_docx_images(file_path)
            if image_refs:
                text = self._append_image_references(text, image_refs)
            self._append_if_nonempty(documents, file_path, text, image_refs=image_refs)

        for file_path_str in classification.text_files:
            file_path = Path(file_path_str)
            self.logger.info(f"Parsing text: {file_path.name}")
            text = await FileTypeRouter.read_text_file(str(file_path))
            self._append_if_nonempty(documents, file_path, text)

        for file_path_str in classification.unsupported:
            self.logger.warning(f"Skipped unsupported file: {Path(file_path_str).name}")

        return documents

    def _extract_parser_text(self, file_path: Path) -> str:
        max_bytes = (
            DocumentValidator.MAX_PDF_SIZE
            if file_path.suffix.lower() == ".pdf"
            else DocumentValidator.MAX_FILE_SIZE
        )
        try:
            return extract_text_from_path(file_path, max_bytes=max_bytes, max_chars=None)
        except (DocumentExtractionError, OSError) as exc:
            self.logger.error(f"Failed to extract {file_path.name}: {exc}")
            return ""

    def _extract_docx_images(self, file_path: Path) -> list[dict[str, str]]:
        if file_path.suffix.lower() != ".docx":
            return []
        asset_dir = file_path.parent / "_assets" / file_path.stem
        refs: list[dict[str, str]] = []
        try:
            with zipfile.ZipFile(file_path) as archive:
                image_names = [
                    name
                    for name in archive.namelist()
                    if name.startswith("word/media/") and not name.endswith("/")
                ]
                for index, member in enumerate(image_names, 1):
                    suffix = Path(member).suffix.lower() or ".bin"
                    target_name = f"image{index}{suffix}"
                    target_path = asset_dir / target_name
                    target_path.parent.mkdir(parents=True, exist_ok=True)
                    target_path.write_bytes(archive.read(member))
                    relative_path = target_path.relative_to(file_path.parent).as_posix()
                    refs.append(
                        {
                            "name": target_name,
                            "path": str(target_path),
                            "relative_path": relative_path,
                            "url": self._raw_file_url(file_path, relative_path),
                        }
                    )
        except (OSError, zipfile.BadZipFile, KeyError) as exc:
            self.logger.warning(f"Failed to extract DOCX images from {file_path.name}: {exc}")
            return []
        if refs:
            self.logger.info(f"Extracted {len(refs)} embedded image(s) from {file_path.name}")
        return refs

    @staticmethod
    def _raw_file_url(file_path: Path, relative_path: str) -> str:
        kb_name = file_path.parent.parent.name
        encoded_kb = quote(kb_name, safe="")
        encoded_path = quote(relative_path, safe="/")
        return f"/api/v1/knowledge/{encoded_kb}/files/{encoded_path}"

    @staticmethod
    def _append_image_references(text: str, image_refs: list[dict[str, str]]) -> str:
        lines = [text.rstrip(), "", "文档内嵌图片:"]
        for index, image in enumerate(image_refs, 1):
            lines.append(f"![文档图片 {index}]({image['url']})")
        return "\n".join(lines).strip()

    def _append_if_nonempty(
        self,
        documents: list[Document],
        file_path: Path,
        text: str,
        *,
        image_refs: list[dict[str, str]] | None = None,
    ) -> None:
        if text.strip():
            metadata = {
                "file_name": file_path.name,
                "file_path": str(file_path),
            }
            if image_refs:
                metadata["images_json"] = json.dumps(image_refs, ensure_ascii=False)
                metadata["image_count"] = len(image_refs)
            documents.append(
                Document(
                    text=text,
                    metadata=metadata,
                )
            )
            self.logger.info(f"Loaded: {file_path.name} ({len(text)} chars)")
        else:
            self.logger.warning(f"Skipped empty document: {file_path.name}")
