package com.monicalab.program.service;

import com.monicalab.program.dto.ProgramRequest;
import com.monicalab.program.dto.ProgramResponse;
import com.monicalab.program.entity.Program;
import com.monicalab.program.entity.RecruitStatus;
import com.monicalab.program.repository.ProgramRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProgramService {

    private final ProgramRepository programRepository;

    @Transactional
    public ProgramResponse create(ProgramRequest request) {
        Program program = Program.builder()
                .programType(request.programType())
                .title(request.title())
                .content(request.content())
                .thumbnail(request.thumbnail())
                .attachment(request.attachment())
                .googleFormUrl(request.googleFormUrl())
                .recruitStatus(request.recruitStatus() != null ? request.recruitStatus() : RecruitStatus.OPEN)
                .isPublic(request.isPublic() != null ? request.isPublic() : false)
                .build();

        return ProgramResponse.from(programRepository.save(program));
    }
}
