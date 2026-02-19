// features/chat/components/message-item/message-item.component.ts

import { Component, Input, OnInit, OnChanges, SimpleChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MarkdownModule } from 'ngx-markdown';
import { Message, Citation } from '../../store/chat.state';

interface GroupedCitation {
  fileName: string;
  indices: number[];
  sourceFile: string | null;
  sourcePage: number | null;
}

@Component({
  selector: 'app-message-item',
  standalone: true,
  imports: [CommonModule, MarkdownModule],
  templateUrl: './message-item.component.html',
  styleUrls: ['./message-item.component.scss']
})
export class MessageItemComponent implements OnInit, OnChanges {
  @Input() message!: Message;
  @Input() isStreaming: boolean = false;
  
  groupedCitations: GroupedCitation[] = [];
  
  ngOnInit(): void {
    this.updateCitations();
  }
  
  ngOnChanges(changes: SimpleChanges): void {
    if (changes['message']) {
      this.updateCitations();
    }
  }
  
  private updateCitations(): void {  
    if (this.message.citations && this.message.citations.length > 0) {
      this.groupedCitations = this.groupCitations(this.message.citations);
    } else {
      this.groupedCitations = [];
    }
  }
  
  private groupCitations(citations: Citation[]): GroupedCitation[] {
    const grouped = new Map<string, GroupedCitation>();
    
    citations.forEach((citation, index) => {
      const fileName = this.parseSourceContent(citation.content);
      console.log(`🔵 Parsed fileName: "${fileName}"`);
      
      if (!fileName) {
        return;
      }
      
      if (grouped.has(fileName)) {
        const existing = grouped.get(fileName)!;
        if (!existing.indices.includes(citation.index)) {
          existing.indices.push(citation.index);
          existing.indices.sort((a, b) => a - b);
        }
      } else {
        grouped.set(fileName, {
          fileName,
          indices: [citation.index],
          sourceFile: citation.sourceFile ?? null,
          sourcePage: citation.sourcePage ?? null
        });
      }
    });
    
    const result = Array.from(grouped.values());
    console.log('✅ Final grouped result:', result);
    return result;
  }
  
  parseSourceContent(content: string): string {
    // Cas 1: Format markdown "[source](filename.pdf)"
    const markdownMatch = content.match(/\[source\]\((.+?)\)/i);
    if (markdownMatch && markdownMatch[1]) {
      return markdownMatch[1].trim();
    }
    
    // Cas 2: Format markdown "[text](filename.pdf)"
    const linkMatch = content.match(/\[.*?\]\((.+?)\)/i);
    if (linkMatch && linkMatch[1]) {
      return linkMatch[1].trim();
    }
    
    // Cas 3: Juste un nom de fichier (notre cas actuel)
    if (content.includes('.pdf') || content.includes('.docx')) {
      return content.trim();
    }
    
    // Cas 4: Retourner vide si aucun pattern ne correspond
    return '';
  }
  
  onSourceClick(citation: GroupedCitation): void {
    console.log('📄 Source clicked:', citation);
    alert(`📄 Fichier: ${citation.fileName}\n📑 Citations: [${citation.indices.join(', ')}]`);
  }
}