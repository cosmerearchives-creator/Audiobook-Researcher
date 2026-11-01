#!/usr/bin/env python3
"""Generate a sherpa-onnx keywords file for the spoken word BOOKMARK.

Uses the exact SentencePiece model packaged with the English GigaSpeech
keyword spotter. This file must contain BPE *pieces* (not integer IDs).
"""
import argparse
from pathlib import Path
import sentencepiece as spm

parser=argparse.ArgumentParser()
parser.add_argument("bpe_model",type=Path)
parser.add_argument("output",type=Path)
args=parser.parse_args()
model=spm.SentencePieceProcessor(model_file=str(args.bpe_model))
pieces=model.encode("BOOKMARK",out_type=str)
if not pieces or any(piece in ("<unk>","<s>","</s>") for piece in pieces):
    raise RuntimeError(f"Unexpected BPE tokens for BOOKMARK: {pieces!r}")
args.output.parent.mkdir(parents=True,exist_ok=True)
args.output.write_text(" ".join(pieces)+" @BOOKMARK\n",encoding="utf-8")
print("Generated BOOKMARK keyword pieces:",pieces)

# Compact on-device BPE vocabulary and piece scores for user-editable phrases.
# This permits re-tokenizing a new wake phrase without building a new APK.
import json
vocab = {model.id_to_piece(i): model.get_score(i) for i in range(model.get_piece_size())}
(args.output.parent / "bpe-vocab.json").write_text(
    json.dumps(vocab, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
